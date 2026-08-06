import { expect } from "chai";
import { ethers } from "hardhat";
import { time } from "@nomicfoundation/hardhat-network-helpers";
import {
  AbiCoder,
  concat,
  keccak256,
  randomBytes,
  toUtf8Bytes,
  type Signer
} from "ethers";
import fixture from "./fixtures/merkle-v1.json";
import type { TrekkeyCredentialRegistryV1 } from "../typechain-types/contracts/TrekkeyCredentialRegistryV1";

const BATCH_TYPES = {
  BatchApproval: [
    { name: "issuerId", type: "bytes32" },
    { name: "batchIdHash", type: "bytes32" },
    { name: "merkleRoot", type: "bytes32" },
    { name: "schemaVersionHash", type: "bytes32" },
    { name: "leafCount", type: "uint32" },
    { name: "treeVersion", type: "uint16" },
    { name: "issuerKeyVersion", type: "uint64" },
    { name: "approvalNonce", type: "uint64" },
    { name: "deadline", type: "uint64" }
  ]
};

const STATUS_TYPES = {
  StatusApproval: [
    { name: "issuerId", type: "bytes32" },
    { name: "credentialIdHash", type: "bytes32" },
    { name: "action", type: "uint8" },
    { name: "replacementCredentialIdHash", type: "bytes32" },
    { name: "effectiveAt", type: "uint64" },
    { name: "issuerKeyVersion", type: "uint64" },
    { name: "approvalNonce", type: "uint64" },
    { name: "deadline", type: "uint64" }
  ]
};

const ZERO_HASH = ethers.ZeroHash;
const hash = (value: string): string => keccak256(toUtf8Bytes(value));
const leafDomain = hash("TREKKEY_CREDENTIAL_LEAF_V1");
const issuerId = hash("issuer:trekkey:demo-university");
const schemaVersionHash = hash("credential-schema:1");
const coder = AbiCoder.defaultAbiCoder();

type BatchApproval = {
  issuerId: string;
  batchIdHash: string;
  merkleRoot: string;
  schemaVersionHash: string;
  leafCount: number;
  treeVersion: number;
  issuerKeyVersion: number;
  approvalNonce: number;
  deadline: number;
};

type StatusApproval = {
  issuerId: string;
  credentialIdHash: string;
  action: number;
  replacementCredentialIdHash: string;
  effectiveAt: number;
  issuerKeyVersion: number;
  approvalNonce: number;
  deadline: number;
};

describe("TrekkeyCredentialRegistryV1", () => {
  let registry: TrekkeyCredentialRegistryV1;
  let admin: Signer;
  let issuerSigner: Signer;
  let relayer: Signer;
  let outsider: Signer;

  beforeEach(async () => {
    [admin, issuerSigner, relayer, outsider] = await ethers.getSigners();
    registry = await deployRegistry(await admin.getAddress());
    await registry.connect(admin).grantRole(await registry.RELAYER_ROLE(), await relayer.getAddress());
    await registry.connect(admin).registerIssuerKey(issuerId, 1, await issuerSigner.getAddress());
  });

  it("anchors a valid issuer-approved batch and returns it", async () => {
    const approval = await batchApproval("batch:valid", hash("root:valid"), 1, 1);
    const signature = await signBatch(approval, issuerSigner);

    await expect(registry.connect(relayer).anchorBatch(approval, signature))
      .to.emit(registry, "BatchAnchored")
      .withArgs(
        issuerId,
        approval.batchIdHash,
        approval.merkleRoot,
        schemaVersionHash,
        1,
        1,
        1,
        await time.latest() + 1
      );

    const [batch, exists] = await registry.getBatch(approval.batchIdHash);
    expect(exists).to.equal(true);
    expect(batch.issuerId).to.equal(issuerId);
    expect(batch.merkleRoot).to.equal(approval.merkleRoot);
    expect(await registry.isApprovalNonceUsed(issuerId, approval.approvalNonce)).to.equal(true);
  });

  it("rejects an unapproved relayer", async () => {
    const approval = await batchApproval("batch:unapproved-relayer", hash("root"), 1, 1);
    const signature = await signBatch(approval, issuerSigner);

    await expect(registry.connect(outsider).anchorBatch(approval, signature))
      .to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
  });

  it("rejects an invalid issuer signature, key version, and expired approval", async () => {
    const invalidSignatureApproval = await batchApproval("batch:wrong-signer", hash("root:signer"), 1, 1);
    await expect(
      registry.connect(relayer).anchorBatch(invalidSignatureApproval, await signBatch(invalidSignatureApproval, outsider))
    ).to.be.revertedWithCustomError(registry, "InvalidIssuerSignature");

    const wrongKeyApproval = await batchApproval("batch:wrong-key", hash("root:key"), 2, 2);
    await expect(registry.connect(relayer).anchorBatch(wrongKeyApproval, await signBatch(wrongKeyApproval, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "IssuerKeyNotFound");

    const expiredApproval = await batchApproval(
      "batch:expired",
      hash("root:expired"),
      1,
      3,
      (await time.latest()) - 1
    );
    await expect(registry.connect(relayer).anchorBatch(expiredApproval, await signBatch(expiredApproval, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "ApprovalExpired");
  });

  it("rejects signatures replayed from another chain or registry address", async () => {
    const approval = await batchApproval("batch:domain", hash("root:domain"), 1, 1);
    const network = await ethers.provider.getNetwork();
    const contractAddress = await registry.getAddress();
    const wrongChainSignature = await issuerSigner.signTypedData(
      domainFor(contractAddress, network.chainId + 1n),
      BATCH_TYPES,
      approval
    );
    await expect(registry.connect(relayer).anchorBatch(approval, wrongChainSignature))
      .to.be.revertedWithCustomError(registry, "InvalidIssuerSignature");

    const secondRegistry = await deployRegistry(await admin.getAddress());
    const wrongContractSignature = await issuerSigner.signTypedData(
      domainFor(await secondRegistry.getAddress(), network.chainId),
      BATCH_TYPES,
      approval
    );
    await expect(registry.connect(relayer).anchorBatch(approval, wrongContractSignature))
      .to.be.revertedWithCustomError(registry, "InvalidIssuerSignature");
  });

  it("rejects both exact digest replay and nonce reuse", async () => {
    const first = await batchApproval("batch:first", hash("root:first"), 1, 7);
    const firstSignature = await signBatch(first, issuerSigner);
    await registry.connect(relayer).anchorBatch(first, firstSignature);

    await expect(registry.connect(relayer).anchorBatch(first, firstSignature))
      .to.be.revertedWithCustomError(registry, "ApprovalDigestAlreadyUsed");

    const nonceReplay = await batchApproval("batch:nonce-replay", hash("root:nonce-replay"), 1, 7);
    await expect(
      registry.connect(relayer).anchorBatch(nonceReplay, await signBatch(nonceReplay, issuerSigner))
    ).to.be.revertedWithCustomError(registry, "ApprovalNonceAlreadyUsed");
  });

  it("rejects a separately approved duplicate batch", async () => {
    const first = await batchApproval("batch:duplicate", hash("root:duplicate"), 1, 1);
    await registry.connect(relayer).anchorBatch(first, await signBatch(first, issuerSigner));

    const duplicate = await batchApproval("batch:duplicate", hash("root:duplicate"), 1, 2);
    await expect(registry.connect(relayer).anchorBatch(duplicate, await signBatch(duplicate, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "BatchAlreadyAnchored");
  });

  it("blocks newly signed approvals after issuer key retirement or compromise", async () => {
    const retireAt = await time.latest();
    await registry.connect(admin).retireIssuerKey(issuerId, 1, retireAt);
    const retiredApproval = await batchApproval("batch:retired", hash("root:retired"), 1, 1);
    await expect(registry.connect(relayer).anchorBatch(retiredApproval, await signBatch(retiredApproval, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "IssuerKeyNotActive");

    const compromisedRegistry = await deployRegistry(await admin.getAddress());
    await compromisedRegistry.connect(admin).grantRole(await compromisedRegistry.RELAYER_ROLE(), await relayer.getAddress());
    await compromisedRegistry.connect(admin).registerIssuerKey(issuerId, 1, await issuerSigner.getAddress());
    const compromisedAt = await time.latest();
    await compromisedRegistry.connect(admin).markIssuerKeyCompromised(issuerId, 1, compromisedAt);
    const compromisedApproval = await batchApproval("batch:compromised", hash("root:compromised"), 1, 1);
    await expect(
      compromisedRegistry.connect(relayer).anchorBatch(compromisedApproval, await signBatch(compromisedApproval, issuerSigner, compromisedRegistry))
    ).to.be.revertedWithCustomError(compromisedRegistry, "IssuerKeyIsCompromised");
  });

  it("pauses anchors without blocking revocation and supersession", async () => {
    await registry.connect(admin).pause();
    const batch = await batchApproval("batch:paused", hash("root:paused"), 1, 1);
    await expect(registry.connect(relayer).anchorBatch(batch, await signBatch(batch, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "EnforcedPause");

    const revoke = await statusApproval("credential:paused-revoke", 0, ZERO_HASH, 2);
    await expect(registry.connect(relayer).revokeCredential(revoke, await signStatus(revoke, issuerSigner)))
      .to.emit(registry, "CredentialRevoked");

    const supersede = await statusApproval("credential:paused-supersede", 1, hash("credential:replacement"), 3);
    await expect(registry.connect(relayer).supersedeCredential(supersede, await signStatus(supersede, issuerSigner)))
      .to.emit(registry, "CredentialSuperseded");
  });

  it("records revoke and supersede once, and refuses a state overwrite", async () => {
    const revokedCredential = "credential:revoked";
    const revoke = await statusApproval(revokedCredential, 0, ZERO_HASH, 1);
    await registry.connect(relayer).revokeCredential(revoke, await signStatus(revoke, issuerSigner));

    const status = await registry.getCredentialStatus(issuerId, hash(revokedCredential));
    expect(status.state).to.equal(1n);
    expect(status.recordedAt).to.be.greaterThanOrEqual(status.effectiveAt);
    expect(status.replacementCredentialIdHash).to.equal(ZERO_HASH);

    const overwrite = await statusApproval(revokedCredential, 1, hash("credential:new"), 2);
    await expect(registry.connect(relayer).supersedeCredential(overwrite, await signStatus(overwrite, issuerSigner)))
      .to.be.revertedWithCustomError(registry, "CredentialStatusAlreadySet");

    const supersededCredential = "credential:superseded";
    const supersede = await statusApproval(supersededCredential, 1, hash("credential:replacement"), 3);
    await registry.connect(relayer).supersedeCredential(supersede, await signStatus(supersede, issuerSigner));
    const supersededStatus = await registry.getCredentialStatus(issuerId, hash(supersededCredential));
    expect(supersededStatus.state).to.equal(2n);
    expect(supersededStatus.recordedAt).to.be.greaterThanOrEqual(supersededStatus.effectiveAt);
    expect(supersededStatus.replacementCredentialIdHash).to.equal(hash("credential:replacement"));
  });

  it("rejects zero or future status effective times", async () => {
    const zeroEffectiveAt = await statusApproval("credential:zero-effective-at", 0, ZERO_HASH, 1);
    zeroEffectiveAt.effectiveAt = 0;
    await expect(
      registry.connect(relayer).revokeCredential(zeroEffectiveAt, await signStatus(zeroEffectiveAt, issuerSigner))
    ).to.be.revertedWithCustomError(registry, "InvalidStatusEffectiveAt");

    const futureEffectiveAt = await statusApproval("credential:future-effective-at", 0, ZERO_HASH, 2);
    futureEffectiveAt.effectiveAt = (await time.latest()) + 3600;
    await expect(
      registry.connect(relayer).revokeCredential(futureEffectiveAt, await signStatus(futureEffectiveAt, issuerSigner))
    ).to.be.revertedWithCustomError(registry, "InvalidStatusEffectiveAt");
  });

  it("verifies OpenZeppelin-compatible Merkle proofs and rejects altered proofs", async () => {
    const leaves = [
      makeLeaf("credential:merkle-a", "content:a", "manifest:a"),
      makeLeaf("credential:merkle-b", "content:b", "manifest:b"),
      makeLeaf("credential:merkle-c", "content:c", "manifest:c")
    ];
    const { StandardMerkleTree } = await import("@openzeppelin/merkle-tree");
    const tree = StandardMerkleTree.of(leaves.map((leaf) => leaf.values), [
      "bytes32",
      "bytes32",
      "bytes32",
      "bytes32",
      "bytes32",
      "bytes32"
    ]);
    const approval = await batchApproval("batch:merkle", tree.root, 1, 1, undefined, 3);
    await registry.connect(relayer).anchorBatch(approval, await signBatch(approval, issuerSigner));

    const proof = tree.getProof(1);
    expect(await registry.verifyProof(approval.batchIdHash, leaves[1].leafHash, proof)).to.equal(true);
    expect(
      await registry.verifyProof(approval.batchIdHash, leaves[1].leafHash, [
        `0x${Buffer.from(randomBytes(32)).toString("hex")}`
      ])
    ).to.equal(false);

    expect(
      await registry.hashLeaf(
        issuerId,
        hash("credential:merkle-b"),
        schemaVersionHash,
        hash("content:b"),
        hash("manifest:b")
      )
    ).to.equal(leaves[1].leafHash);
  });

  it("keeps the committed one-, two-, three-, and five-leaf fixture byte-for-byte reproducible", async () => {
    const { StandardMerkleTree } = await import("@openzeppelin/merkle-tree");

    for (const fixtureCase of fixture.cases) {
      const values = fixtureCase.leaves.map((leaf) => [
        fixture.leafDomain,
        leaf.issuerId,
        leaf.credentialIdHash,
        leaf.schemaVersionHash,
        leaf.contentHash,
        leaf.fileManifestHash
      ]);
      const tree = StandardMerkleTree.of(values, fixture.leafTypes);
      expect(tree.root).to.equal(fixtureCase.root);
      expect([...fixtureCase.leaves].map((leaf) => leaf.leafHash).sort()).to.deep.equal(fixtureCase.sortedLeaves);

      for (const [valueIndex, leaf] of fixtureCase.leaves.entries()) {
        const encodedLeafValue = coder.encode(fixture.leafTypes, values[valueIndex]);
        expect(encodedLeafValue).to.equal(leaf.encodedLeafValue);
        expect(keccak256(concat([keccak256(encodedLeafValue)]))).to.equal(leaf.leafHash);
        expect(tree.getProof(valueIndex)).to.deep.equal(leaf.proof);
        expect(fixtureCase.sortedLeaves[leaf.leafIndex]).to.equal(leaf.leafHash);
        expect(
          await registry.hashLeaf(
            leaf.issuerId,
            leaf.credentialIdHash,
            leaf.schemaVersionHash,
            leaf.contentHash,
            leaf.fileManifestHash
          )
        ).to.equal(leaf.leafHash);
      }
    }
  });

  async function batchApproval(
    batchId: string,
    merkleRoot: string,
    issuerKeyVersion: number,
    approvalNonce: number,
    deadline?: number,
    leafCount = 1
  ): Promise<BatchApproval> {
    return {
      issuerId,
      batchIdHash: hash(batchId),
      merkleRoot,
      schemaVersionHash,
      leafCount,
      treeVersion: 1,
      issuerKeyVersion,
      approvalNonce,
      deadline: deadline ?? (await time.latest()) + 3600
    };
  }

  async function statusApproval(
    credentialId: string,
    action: number,
    replacementCredentialIdHash: string,
    approvalNonce: number
  ): Promise<StatusApproval> {
    return {
      issuerId,
      credentialIdHash: hash(credentialId),
      action,
      replacementCredentialIdHash,
      effectiveAt: await time.latest(),
      issuerKeyVersion: 1,
      approvalNonce,
      deadline: (await time.latest()) + 3600
    };
  }

  async function signBatch(
    approval: BatchApproval,
    signer: Signer,
    targetRegistry = registry
  ): Promise<string> {
    const network = await ethers.provider.getNetwork();
    return signer.signTypedData(domainFor(await targetRegistry.getAddress(), network.chainId), BATCH_TYPES, approval);
  }

  async function signStatus(approval: StatusApproval, signer: Signer): Promise<string> {
    const network = await ethers.provider.getNetwork();
    return signer.signTypedData(domainFor(await registry.getAddress(), network.chainId), STATUS_TYPES, approval);
  }
});

async function deployRegistry(initialAdmin: string): Promise<TrekkeyCredentialRegistryV1> {
  const factory = await ethers.getContractFactory("TrekkeyCredentialRegistryV1");
  const registry = (await factory.deploy(initialAdmin)) as unknown as TrekkeyCredentialRegistryV1;
  await registry.waitForDeployment();
  return registry;
}

function domainFor(verifyingContract: string, chainId: bigint) {
  return {
    name: "TrekkeyCredentialRegistry",
    version: "1",
    chainId,
    verifyingContract
  };
}

function makeLeaf(credentialId: string, content: string, manifest: string) {
  const values = [
    leafDomain,
    issuerId,
    hash(credentialId),
    schemaVersionHash,
    hash(content),
    hash(manifest)
  ];
  const encoded = coder.encode(["bytes32", "bytes32", "bytes32", "bytes32", "bytes32", "bytes32"], values);
  return {
    values,
    leafHash: keccak256(concat([keccak256(encoded)]))
  };
}

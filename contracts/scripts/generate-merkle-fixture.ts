import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { StandardMerkleTree } from "@openzeppelin/merkle-tree";
import { AbiCoder, concat, keccak256, toUtf8Bytes } from "ethers";

const TYPES = ["bytes32", "bytes32", "bytes32", "bytes32", "bytes32", "bytes32"];
const coder = AbiCoder.defaultAbiCoder();
const leafDomain = keccak256(toUtf8Bytes("TREKKEY_CREDENTIAL_LEAF_V1"));

type CredentialLeaf = {
  issuerId: string;
  credentialIdHash: string;
  schemaVersionHash: string;
  contentHash: string;
  fileManifestHash: string;
};

type FixtureCase = {
  name: string;
  leaves: CredentialLeaf[];
};

const hash = (value: string): string => keccak256(toUtf8Bytes(value));

const issuerId = hash("issuer:trekkey:demo-university");
const schemaVersionHash = hash("credential-schema:1");
const sharedContentHash = hash("credential-content:shared");
const sharedManifestHash = hash("file-manifest:shared");

const cases: FixtureCase[] = [
  {
    name: "one-leaf",
    leaves: [
      {
        issuerId,
        credentialIdHash: hash("credential:one"),
        schemaVersionHash,
        contentHash: hash("credential-content:one"),
        fileManifestHash: hash("file-manifest:one")
      }
    ]
  },
  {
    name: "two-leaves-same-content-different-credential-id",
    leaves: [
      {
        issuerId,
        credentialIdHash: hash("credential:two-a"),
        schemaVersionHash,
        contentHash: sharedContentHash,
        fileManifestHash: sharedManifestHash
      },
      {
        issuerId,
        credentialIdHash: hash("credential:two-b"),
        schemaVersionHash,
        contentHash: sharedContentHash,
        fileManifestHash: sharedManifestHash
      }
    ]
  },
  {
    name: "three-leaves-odd-count-and-content-change",
    leaves: [
      {
        issuerId,
        credentialIdHash: hash("credential:three-a"),
        schemaVersionHash,
        contentHash: hash("credential-content:three-a"),
        fileManifestHash: hash("file-manifest:three-a")
      },
      {
        issuerId,
        credentialIdHash: hash("credential:three-b"),
        schemaVersionHash,
        contentHash: hash("credential-content:three-b"),
        fileManifestHash: hash("file-manifest:three-b")
      },
      {
        issuerId,
        credentialIdHash: hash("credential:three-c"),
        schemaVersionHash,
        contentHash: hash("credential-content:three-c-changed"),
        fileManifestHash: hash("file-manifest:three-c")
      }
    ]
  },
  {
    name: "five-leaves-complete-tree-layout",
    leaves: Array.from({ length: 5 }, (_, index) => ({
      issuerId,
      credentialIdHash: hash(`credential:five-${index + 1}`),
      schemaVersionHash,
      contentHash: hash(`credential-content:five-${index + 1}`),
      fileManifestHash: hash(`file-manifest:five-${index + 1}`)
    }))
  }
];

function valuesFor(leaf: CredentialLeaf): string[] {
  return [
    leafDomain,
    leaf.issuerId,
    leaf.credentialIdHash,
    leaf.schemaVersionHash,
    leaf.contentHash,
    leaf.fileManifestHash
  ];
}

const fixture = {
  treeVersion: 1,
  leafDomain,
  leafTypes: TYPES,
  hashRule: "leafHash = keccak256(bytes.concat(keccak256(abi.encode(leaf tuple))))",
  nodeRule: "nodeHash = keccak256(sorted(left, right))",
  cases: cases.map((fixtureCase) => {
    const values = fixtureCase.leaves.map(valuesFor);
    const tree = StandardMerkleTree.of(values, TYPES);
    const entries = fixtureCase.leaves.map((leaf, valueIndex) => {
      const encodedLeafValue = coder.encode(TYPES, valuesFor(leaf));
      const leafHash = keccak256(concat([keccak256(encodedLeafValue)]));
      return {
        ...leaf,
        encodedLeafValue,
        leafHash,
        leafIndex: -1,
        proof: tree.getProof(valueIndex)
      };
    });
    const sortedLeaves = entries.map(({ leafHash }) => leafHash).sort();

    return {
      name: fixtureCase.name,
      root: tree.root,
      sortedLeaves,
      leaves: entries.map((entry) => ({
        ...entry,
        leafIndex: sortedLeaves.indexOf(entry.leafHash)
      }))
    };
  })
};

const destination = resolve(process.cwd(), "test/fixtures/merkle-v1.json");
writeFileSync(destination, `${JSON.stringify(fixture, null, 2)}\n`);
console.log(`Wrote ${destination}`);

import {
  AbiCoder,
  BrowserProvider,
  Contract,
  ContractFactory,
  ZeroAddress,
  ZeroHash,
  formatEther,
  getAddress,
  isAddress,
  keccak256,
  toUtf8Bytes,
  verifyTypedData
} from "/ethers.js";
import {
  deploymentStorageKey,
  isUuidV4,
  issuerProofStorageKey,
  normalizeRuntimeCode,
  parseDeploymentState,
  parseIssuerProof
} from "/core.mjs";

const ISSUER_ID_DOMAIN = keccak256(toUtf8Bytes("TREKKEY_ISSUER_ID_V1"));
const EXPECTED_LEAF_DOMAIN = keccak256(toUtf8Bytes("TREKKEY_CREDENTIAL_LEAF_V1"));
const ISSUER_PROOF_STATEMENT_HASH = keccak256(
  toUtf8Bytes("TREKKEY_ISSUER_KEY_PROOF_V1")
);
const ISSUER_PROOF_TYPES = {
  IssuerKeyProof: [
    { name: "issuerId", type: "bytes32" },
    { name: "keyVersion", type: "uint64" },
    { name: "signer", type: "address" },
    { name: "statementHash", type: "bytes32" }
  ]
};
const KAIROS_CHAIN_ID = 1001n;

const elements = {
  account: requiredElement("account-value"),
  balance: requiredElement("balance-value"),
  badge: requiredElement("connection-badge"),
  clearLog: requiredElement("clear-log-button"),
  configure: requiredElement("configure-button"),
  connect: requiredElement("connect-button"),
  copyRegistry: requiredElement("copy-registry-button"),
  deploy: requiredElement("deploy-button"),
  expectedDeployer: requiredElement("expected-deployer"),
  issuerPublicId: requiredElement("issuer-public-id"),
  issuerSigner: requiredElement("issuer-signer"),
  log: requiredElement("activity-log"),
  network: requiredElement("network-value"),
  proveIssuer: requiredElement("prove-issuer-button"),
  registryAddress: requiredElement("registry-address"),
  registryStatus: requiredElement("registry-status-value"),
  relayerAddress: requiredElement("relayer-address"),
  verify: requiredElement("verify-button")
};

let artifact;
let config;
let runtime;
let provider;
let signer;
let connectedAddress;
let connectedRole;
let issuerProof;
let busy = false;
let initialized = false;
let listenersAttached = false;

function requiredElement(id) {
  const element = document.getElementById(id);
  if (element === null) {
    throw new Error(`Missing required element: ${id}`);
  }
  return element;
}

function errorMessage(error) {
  if (error?.code === 4001 || error?.code === "ACTION_REJECTED") {
    return "Kaia Wallet에서 요청을 취소했습니다.";
  }
  return error?.shortMessage ?? error?.reason ?? error?.message ?? String(error);
}

function shortAddress(address) {
  if (!address || address.length < 12) {
    return address || "-";
  }
  return `${address.slice(0, 8)}...${address.slice(-6)}`;
}

function addLog(message, level = "info", link = null) {
  const entry = document.createElement("li");
  entry.className = "activity-entry";
  entry.dataset.level = level;

  const timestamp = document.createElement("time");
  timestamp.dateTime = new Date().toISOString();
  timestamp.textContent = new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date());

  const text = document.createElement("p");
  text.textContent = message;
  if (link !== null) {
    const anchor = document.createElement("a");
    anchor.href = link.href;
    anchor.target = "_blank";
    anchor.rel = "noopener noreferrer";
    anchor.textContent = link.label;
    text.append(anchor);
  }

  entry.append(timestamp, text);
  elements.log.prepend(entry);
}

function setConnectionState(state, label) {
  elements.badge.dataset.state = state;
  elements.badge.textContent = label;
}

function clearConnectionDisplay() {
  elements.network.textContent = "-";
  elements.account.textContent = "-";
  elements.account.removeAttribute("title");
  elements.balance.textContent = "-";
}

function setBusy(nextBusy) {
  busy = nextBusy;
  refreshActions();
}

function normalizedAddress(value, label) {
  if (!isAddress(value)) {
    throw new Error(`${label} 주소가 유효하지 않습니다.`);
  }
  return getAddress(value);
}

function registryAddressOrNull() {
  const value = elements.registryAddress.value.trim();
  return isAddress(value) ? getAddress(value) : null;
}

function refreshActions() {
  const connected = provider !== undefined && signer !== undefined && connectedAddress !== undefined;
  const hasRegistry = registryAddressOrNull() !== null;
  elements.connect.disabled = busy || !initialized;
  elements.deploy.disabled = busy || !connected || connectedRole !== "deployer" || hasRegistry;
  elements.proveIssuer.disabled =
    busy ||
    !connected ||
    connectedRole !== "issuer" ||
    !hasRegistry ||
    issuerProofIsValid();
  elements.configure.disabled =
    busy ||
    !connected ||
    connectedRole !== "deployer" ||
    !hasRegistry ||
    !issuerProofIsValid();
  elements.verify.disabled =
    busy || !connected || connectedRole !== "deployer" || !hasRegistry;
  elements.copyRegistry.disabled = !hasRegistry;
}

function computeIssuerId(publicId) {
  if (!isUuidV4(publicId)) {
    throw new Error("학교 issuer public ID는 UUIDv4 형식이어야 합니다.");
  }
  return keccak256(
    AbiCoder.defaultAbiCoder().encode(
      ["bytes32", "bytes32"],
      [ISSUER_ID_DOMAIN, keccak256(toUtf8Bytes(publicId))]
    )
  );
}

function issuerProofDomain(registryAddress) {
  return {
    name: "TrekkeyCredentialRegistry",
    version: "1",
    chainId: KAIROS_CHAIN_ID,
    verifyingContract: registryAddress
  };
}

function issuerProofMessage(issuerSignerAddress) {
  return {
    issuerId: computeIssuerId(config.issuerPublicId),
    keyVersion: issuerKeyVersion(),
    signer: issuerSignerAddress,
    statementHash: ISSUER_PROOF_STATEMENT_HASH
  };
}

function issuerKeyVersion() {
  let value;
  try {
    value = BigInt(config.issuerKeyVersion);
  } catch {
    throw new Error("Issuer key version은 정수여야 합니다.");
  }
  if (value <= 0n || value > (1n << 64n) - 1n) {
    throw new Error("Issuer key version은 양의 uint64여야 합니다.");
  }
  return value;
}

function issuerProofIsValid() {
  const registryAddress = registryAddressOrNull();
  if (issuerProof === undefined || issuerProof === null || registryAddress === null) {
    return false;
  }
  try {
    const issuerSigner = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    if (
      issuerProof.issuerId !== computeIssuerId(config.issuerPublicId) ||
      issuerProof.keyVersion !== issuerKeyVersion().toString()
    ) {
      return false;
    }
    const recovered = getAddress(
      verifyTypedData(
        issuerProofDomain(registryAddress),
        ISSUER_PROOF_TYPES,
        issuerProofMessage(issuerSigner),
        issuerProof.signature
      )
    );
    return recovered === issuerSigner;
  } catch {
    return false;
  }
}

function setRegistryAddress(address, status = null) {
  const normalized = normalizedAddress(address, "Registry");
  elements.registryAddress.value = normalized;
  elements.registryStatus.textContent = status ?? shortAddress(normalized);
  elements.registryStatus.title = normalized;
  loadIssuerProof();
  refreshActions();
  return normalized;
}

function deploymentState() {
  const expectedDeployer = normalizedAddress(
    config.expectedDeployerAddress,
    "예상 deployer"
  );
  return parseDeploymentState(
    localStorage.getItem(deploymentStorageKey(expectedDeployer)),
    expectedDeployer
  );
}

function saveDeploymentState(registryAddress, transactionHash) {
  const expectedDeployer = normalizedAddress(
    config.expectedDeployerAddress,
    "예상 deployer"
  );
  localStorage.setItem(
    deploymentStorageKey(expectedDeployer),
    JSON.stringify({
      version: 1,
      chainId: Number(KAIROS_CHAIN_ID),
      deployerAddress: expectedDeployer,
      registryAddress: normalizedAddress(registryAddress, "Registry"),
      transactionHash,
      createdAt: new Date().toISOString()
    })
  );
}

function clearDeploymentState() {
  const expectedDeployer = normalizedAddress(
    config.expectedDeployerAddress,
    "예상 deployer"
  );
  localStorage.removeItem(deploymentStorageKey(expectedDeployer));
}

function loadIssuerProof() {
  issuerProof = null;
  const registryAddress = registryAddressOrNull();
  if (registryAddress === null || config === undefined) {
    return;
  }
  const issuerSigner = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
  issuerProof = parseIssuerProof(
    localStorage.getItem(issuerProofStorageKey(registryAddress, issuerSigner)),
    registryAddress,
    issuerSigner
  );
}

function saveIssuerProof(registryAddress, issuerSigner, signature) {
  issuerProof = {
    version: 1,
    chainId: Number(KAIROS_CHAIN_ID),
    registryAddress,
    issuerSignerAddress: issuerSigner,
    issuerId: computeIssuerId(config.issuerPublicId),
    keyVersion: issuerKeyVersion().toString(),
    signature,
    createdAt: new Date().toISOString()
  };
  localStorage.setItem(
    issuerProofStorageKey(registryAddress, issuerSigner),
    JSON.stringify(issuerProof)
  );
}

async function assertRegistryRuntime(registryAddress) {
  const code = await provider.getCode(registryAddress);
  if (code === "0x") {
    throw new Error("Registry 주소에 컨트랙트 코드가 없습니다.");
  }
  const expected = normalizeRuntimeCode(
    artifact.deployedBytecode,
    runtime.immutableReferences
  );
  const actual = normalizeRuntimeCode(code, runtime.immutableReferences);
  if (actual !== expected) {
    throw new Error("Registry runtime bytecode가 TrekkeyCredentialRegistryV1과 다릅니다.");
  }
  return code;
}

async function recoverDeployment() {
  const saved = deploymentState();
  if (saved === null) {
    return;
  }
  const registryAddress = setRegistryAddress(saved.registryAddress, "복구 확인 중");
  const code = await provider.getCode(registryAddress);
  if (code !== "0x") {
    await assertRegistryRuntime(registryAddress);
    elements.registryStatus.textContent = shortAddress(registryAddress);
    addLog(`저장된 Registry 복구 완료: ${registryAddress}`, "success");
    return;
  }

  const receipt = await provider.getTransactionReceipt(saved.transactionHash);
  if (receipt === null) {
    elements.registryStatus.textContent = "배포 확인 중";
    addLog("저장된 배포 트랜잭션이 아직 확인되지 않았습니다.", "info", {
      href: `${config.explorerUrl}/tx/${saved.transactionHash}`,
      label: "Kaiascan"
    });
    return;
  }
  if (receipt.status === 0) {
    clearDeploymentState();
    elements.registryAddress.value = "";
    elements.registryStatus.textContent = "배포 실패";
    elements.registryStatus.removeAttribute("title");
    addLog("저장된 배포 트랜잭션이 실패해 복구 상태를 제거했습니다.", "error");
    return;
  }
  throw new Error("배포 영수증은 성공했지만 예상 Registry 코드가 없습니다.");
}

async function switchToKairos(wallet) {
  if (String(wallet.networkVersion) === String(KAIROS_CHAIN_ID)) {
    return;
  }
  const parameters = [{ chainId: "0x3e9" }];
  try {
    await wallet.request({ method: "wallet_switchKlaytnChain", params: parameters });
  } catch (firstError) {
    try {
      await wallet.request({ method: "wallet_switchEthereumChain", params: parameters });
    } catch {
      throw firstError;
    }
  }
}

async function requireCurrentConnection(expectedRole) {
  if (provider === undefined || signer === undefined || connectedAddress === undefined) {
    throw new Error("Kaia Wallet을 먼저 연결해야 합니다.");
  }
  const network = await provider.getNetwork();
  if (network.chainId !== KAIROS_CHAIN_ID) {
    throw new Error(`Kairos chainId 1001이 아닙니다: ${network.chainId}`);
  }
  const currentAddress = getAddress(await signer.getAddress());
  if (currentAddress !== connectedAddress) {
    resetConnection("Kaia Wallet 계정이 변경됐습니다. 다시 연결하세요.");
    throw new Error("연결 계정이 변경됐습니다.");
  }
  if (connectedRole !== expectedRole) {
    throw new Error(
      expectedRole === "deployer"
        ? "Deployer 계정으로 다시 연결해야 합니다."
        : "Issuer signer 계정으로 다시 연결해야 합니다."
    );
  }
  return { currentAddress, network };
}

async function connectWallet() {
  setBusy(true);
  try {
    const wallet = window.klaytn;
    if (wallet === undefined || typeof wallet.enable !== "function") {
      throw new Error("Kaia Wallet 크롬 확장 프로그램을 찾지 못했습니다.");
    }

    await switchToKairos(wallet);
    const accounts = await wallet.enable();
    if (!Array.isArray(accounts) || accounts.length === 0) {
      throw new Error("Kaia Wallet에서 연결 계정을 받지 못했습니다.");
    }

    const nextProvider = new BrowserProvider(wallet, "any");
    const network = await nextProvider.getNetwork();
    if (network.chainId !== KAIROS_CHAIN_ID) {
      throw new Error(`Kairos chainId 1001이 아닙니다: ${network.chainId}`);
    }

    const nextSigner = await nextProvider.getSigner();
    const nextAddress = getAddress(await nextSigner.getAddress());
    const expectedDeployer = normalizedAddress(
      config.expectedDeployerAddress,
      "예상 deployer"
    );
    const expectedIssuer = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    let nextRole;
    if (nextAddress === expectedDeployer) {
      nextRole = "deployer";
    } else if (nextAddress === expectedIssuer) {
      nextRole = "issuer";
    } else {
      throw new Error(
        `허용되지 않은 계정입니다. 현재 ${nextAddress}, deployer ${expectedDeployer}, issuer ${expectedIssuer}`
      );
    }

    provider = nextProvider;
    signer = nextSigner;
    connectedAddress = nextAddress;
    connectedRole = nextRole;

    const balance = await provider.getBalance(nextAddress);
    elements.network.textContent = `Kaia Kairos (${network.chainId})`;
    elements.account.textContent = `${nextRole === "deployer" ? "deployer" : "issuer"} · ${shortAddress(nextAddress)}`;
    elements.account.title = nextAddress;
    elements.balance.textContent = `${Number.parseFloat(formatEther(balance)).toFixed(4)} KAIA`;
    setConnectionState("connected", "연결됨");
    addLog(
      `${nextRole === "deployer" ? "Deployer" : "Issuer signer"} 연결 완료: ${nextAddress}`,
      "success"
    );
    if (nextRole === "deployer") {
      await recoverDeployment();
    }

    if (typeof wallet.on === "function" && !listenersAttached) {
      wallet.on("accountsChanged", () => resetConnection("Kaia Wallet 계정이 변경됐습니다."));
      wallet.on("networkChanged", () => resetConnection("Kaia Wallet 네트워크가 변경됐습니다."));
      listenersAttached = true;
    }
  } catch (error) {
    provider = undefined;
    signer = undefined;
    connectedAddress = undefined;
    connectedRole = undefined;
    clearConnectionDisplay();
    setConnectionState("error", "연결 실패");
    addLog(errorMessage(error), "error");
  } finally {
    setBusy(false);
  }
}

function resetConnection(message) {
  provider = undefined;
  signer = undefined;
  connectedAddress = undefined;
  connectedRole = undefined;
  clearConnectionDisplay();
  setConnectionState("idle", "다시 연결 필요");
  addLog(message, "error");
  refreshActions();
}

async function deployRegistry() {
  setBusy(true);
  try {
    const { currentAddress } = await requireCurrentConnection("deployer");
    const factory = new ContractFactory(artifact.abi, artifact.bytecode, signer);
    addLog("Registry 배포 승인을 요청했습니다.");
    const contract = await factory.deploy(currentAddress);
    const transaction = contract.deploymentTransaction();
    if (transaction === null) {
      throw new Error("배포 트랜잭션을 생성하지 못했습니다.");
    }
    const address = getAddress(await contract.getAddress());
    saveDeploymentState(address, transaction.hash);
    setRegistryAddress(address, "배포 확인 중");
    addLog("배포 트랜잭션이 전송됐습니다.", "info", {
      href: `${config.explorerUrl}/tx/${transaction.hash}`,
      label: "Kaiascan"
    });

    await contract.waitForDeployment();
    await assertRegistryRuntime(address);
    setRegistryAddress(address);
    addLog(`Registry 배포 완료: ${address}`, "success", {
      href: `${config.explorerUrl}/address/${address}`,
      label: "Kaiascan"
    });
    addLog("Kaia Wallet을 issuer signer 계정으로 전환한 뒤 다시 연결하세요.");
  } catch (error) {
    addLog(errorMessage(error), "error");
  } finally {
    setBusy(false);
  }
}

async function waitForTransaction(transaction, label) {
  addLog(`${label} 트랜잭션이 전송됐습니다.`, "info", {
    href: `${config.explorerUrl}/tx/${transaction.hash}`,
    label: "Kaiascan"
  });
  await transaction.wait();
  addLog(`${label} 완료`, "success");
}

async function proveIssuerSigner() {
  setBusy(true);
  try {
    const { currentAddress } = await requireCurrentConnection("issuer");
    const registryAddress = registryAddressOrNull();
    if (registryAddress === null) {
      throw new Error("Registry address가 유효하지 않습니다.");
    }
    await assertRegistryRuntime(registryAddress);
    const issuerSigner = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    if (currentAddress !== issuerSigner) {
      throw new Error("연결 계정이 설정된 issuer signer와 다릅니다.");
    }

    const domain = issuerProofDomain(registryAddress);
    const message = issuerProofMessage(issuerSigner);
    addLog("Issuer signer EIP-712 서명을 요청했습니다.");
    const signature = await signer.signTypedData(domain, ISSUER_PROOF_TYPES, message);
    const recovered = getAddress(
      verifyTypedData(domain, ISSUER_PROOF_TYPES, message, signature)
    );
    if (recovered !== issuerSigner) {
      throw new Error(
        `서명 복구 주소가 issuer signer와 다릅니다. 복구 ${recovered}, 예상 ${issuerSigner}`
      );
    }

    saveIssuerProof(registryAddress, issuerSigner, signature);
    addLog(`Issuer signer 서명 검증 완료: ${recovered}`, "success");
    addLog("Kaia Wallet을 deployer 계정으로 전환한 뒤 다시 연결하세요.");
  } catch (error) {
    addLog(errorMessage(error), "error");
  } finally {
    setBusy(false);
  }
}

async function configureRegistry() {
  setBusy(true);
  try {
    const { currentAddress } = await requireCurrentConnection("deployer");
    const registryAddress = registryAddressOrNull();
    if (registryAddress === null) {
      throw new Error("Registry address가 유효하지 않습니다.");
    }
    const issuerSigner = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    const relayer = normalizedAddress(config.relayerAddress, "Relayer");
    const keyVersion = issuerKeyVersion();
    const issuerId = computeIssuerId(config.issuerPublicId);
    await assertRegistryRuntime(registryAddress);
    if (!issuerProofIsValid()) {
      throw new Error("Issuer signer EIP-712 서명 검증이 필요합니다.");
    }
    const contract = new Contract(registryAddress, artifact.abi, signer);

    const adminRole = await contract.DEFAULT_ADMIN_ROLE();
    const issuerAdminRole = await contract.ISSUER_KEY_ADMIN_ROLE();
    if (!(await contract.hasRole(adminRole, currentAddress))) {
      throw new Error("연결 계정에 DEFAULT_ADMIN_ROLE이 없습니다.");
    }
    if (!(await contract.hasRole(issuerAdminRole, currentAddress))) {
      throw new Error("연결 계정에 ISSUER_KEY_ADMIN_ROLE이 없습니다.");
    }

    const relayerRole = await contract.RELAYER_ROLE();
    if (!(await contract.hasRole(relayerRole, relayer))) {
      addLog("Relayer 역할 부여 승인을 요청했습니다.");
      await waitForTransaction(await contract.grantRole(relayerRole, relayer), "Relayer 역할 부여");
    } else {
      addLog("Relayer 역할이 이미 설정돼 있습니다.", "success");
    }

    const currentKey = await contract.getIssuerKey(issuerId, keyVersion);
    const currentSigner = getAddress(currentKey.signer);
    if (currentSigner === ZeroAddress) {
      addLog("Issuer signer 등록 승인을 요청했습니다.");
      await waitForTransaction(
        await contract.registerIssuerKey(issuerId, keyVersion, issuerSigner),
        "Issuer signer 등록"
      );
    } else if (currentSigner !== issuerSigner) {
      throw new Error(
        `Issuer key version ${keyVersion}이 다른 signer에 이미 등록돼 있습니다: ${currentSigner}`
      );
    } else {
      addLog("Issuer signer가 이미 설정돼 있습니다.", "success");
    }

    addLog(`Issuer ID: ${issuerId}`, "success");
  } catch (error) {
    addLog(errorMessage(error), "error");
  } finally {
    setBusy(false);
  }
}

async function verifyRegistry() {
  setBusy(true);
  try {
    const { currentAddress } = await requireCurrentConnection("deployer");
    const registryAddress = registryAddressOrNull();
    if (registryAddress === null) {
      throw new Error("Registry address가 유효하지 않습니다.");
    }
    const issuerSigner = normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    const relayer = normalizedAddress(config.relayerAddress, "Relayer");
    const issuerId = computeIssuerId(config.issuerPublicId);
    const keyVersion = issuerKeyVersion();
    const contract = new Contract(registryAddress, artifact.abi, provider);

    await assertRegistryRuntime(registryAddress);
    const adminRole = await contract.DEFAULT_ADMIN_ROLE();
    const issuerAdminRole = await contract.ISSUER_KEY_ADMIN_ROLE();
    const relayerRole = await contract.RELAYER_ROLE();
    const currentKey = await contract.getIssuerKey(issuerId, keyVersion);
    const checks = [
      ["Issuer signer proof", issuerProofIsValid()],
      ["초기 관리자", await contract.hasRole(adminRole, currentAddress)],
      ["Issuer 관리자", await contract.hasRole(issuerAdminRole, currentAddress)],
      ["Relayer 역할", await contract.hasRole(relayerRole, relayer)],
      ["Issuer signer", getAddress(currentKey.signer) === issuerSigner],
      ["Leaf domain", (await contract.LEAF_DOMAIN()) === EXPECTED_LEAF_DOMAIN],
      ["DEFAULT_ADMIN_ROLE", adminRole === ZeroHash]
    ];
    const failed = checks.filter(([, passed]) => !passed).map(([name]) => name);
    if (failed.length > 0) {
      throw new Error(`온체인 검증 실패: ${failed.join(", ")}`);
    }

    elements.registryStatus.textContent = "검증 완료";
    addLog("온체인 코드·권한·issuer 설정 검증 완료", "success", {
      href: `${config.explorerUrl}/address/${registryAddress}`,
      label: "Kaiascan"
    });
  } catch (error) {
    addLog(errorMessage(error), "error");
  } finally {
    setBusy(false);
  }
}

async function copyRegistryAddress() {
  const registryAddress = registryAddressOrNull();
  if (registryAddress === null) {
    return;
  }
  await navigator.clipboard.writeText(registryAddress);
  addLog("Registry address를 복사했습니다.", "success");
}

async function initialize() {
  try {
    [config, artifact, runtime] = await Promise.all([
      fetch("/config.json", { cache: "no-store" }).then((response) => {
        if (!response.ok) {
          throw new Error("공개 배포 설정을 읽지 못했습니다.");
        }
        return response.json();
      }),
      fetch("/artifact.json", { cache: "no-store" }).then((response) => {
        if (!response.ok) {
          throw new Error("컨트랙트 artifact를 읽지 못했습니다.");
        }
        return response.json();
      }),
      fetch("/runtime-metadata.json", { cache: "no-store" }).then((response) => {
        if (!response.ok) {
          throw new Error("컨트랙트 runtime metadata를 읽지 못했습니다.");
        }
        return response.json();
      })
    ]);

    elements.expectedDeployer.value = config.expectedDeployerAddress;
    elements.issuerPublicId.value = config.issuerPublicId;
    elements.issuerSigner.value = config.issuerSignerAddress;
    elements.relayerAddress.value = config.relayerAddress;

    normalizedAddress(config.expectedDeployerAddress, "예상 deployer");
    normalizedAddress(config.issuerSignerAddress, "Issuer signer");
    normalizedAddress(config.relayerAddress, "Relayer");
    if (BigInt(config.expectedChainId) !== KAIROS_CHAIN_ID) {
      throw new Error("배포 설정의 chainId가 Kairos 1001이 아닙니다.");
    }
    computeIssuerId(config.issuerPublicId);
    issuerKeyVersion();
    if (
      artifact.contractName !== "TrekkeyCredentialRegistryV1" ||
      artifact.bytecode === "0x" ||
      artifact.deployedBytecode === "0x"
    ) {
      throw new Error("TrekkeyCredentialRegistryV1 artifact가 유효하지 않습니다.");
    }
    normalizeRuntimeCode(artifact.deployedBytecode, runtime.immutableReferences);

    if (isAddress(config.registryAddress)) {
      setRegistryAddress(config.registryAddress);
    } else {
      const saved = deploymentState();
      if (saved !== null) {
        setRegistryAddress(saved.registryAddress, "복구 대기");
      }
    }
    loadIssuerProof();

    initialized = true;
    addLog("로컬 배포 설정을 불러왔습니다.", "success");
  } catch (error) {
    setConnectionState("error", "설정 오류");
    addLog(errorMessage(error), "error");
  }
  refreshActions();
}

elements.connect.addEventListener("click", connectWallet);
elements.deploy.addEventListener("click", deployRegistry);
elements.proveIssuer.addEventListener("click", proveIssuerSigner);
elements.configure.addEventListener("click", configureRegistry);
elements.verify.addEventListener("click", verifyRegistry);
elements.copyRegistry.addEventListener("click", () => {
  copyRegistryAddress().catch((error) => addLog(errorMessage(error), "error"));
});
elements.clearLog.addEventListener("click", () => elements.log.replaceChildren());
elements.registryAddress.addEventListener("input", refreshActions);

initialize().catch((error) => addLog(errorMessage(error), "error"));

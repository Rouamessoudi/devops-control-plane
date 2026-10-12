export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: UserProfile;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ApplicationView {
  id: string;
  ownerId: string;
  name: string;
  description: string | null;
  gitRepositoryUrl: string;
  branch: string;
  environment: string;
  dockerfilePath: string;
  contextPath: string;
  containerPort: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface EnvVarView {
  id?: string;
  key: string;
  value: string;
  secret: boolean;
}

export interface DeploymentView {
  id: string;
  applicationId: string;
  requestedBy: string;
  imageRepository: string;
  imageTag: string;
  fullImage: string;
  replicas: number;
  environment: string;
  trigger: string;
  status: string;
  k8sDeploymentName: string;
  k8sRevision: number | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface BuildView {
  id: string;
  applicationId: string;
  status: string;
  imageName: string;
  imageTag: string;
  fullImage: string;
  logText: string | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  steps: Array<{ name: string; status: string; log: string }>;
}

export interface PipelineView {
  id: string;
  applicationId: string;
  requestedBy: string;
  repositoryUrl: string;
  branch: string;
  status: string;
  trigger: string;
  conclusion: string | null;
  startedAt: string;
  finishedAt: string | null;
  steps: Array<{ name: string; status: string; log: string; startedAt: string | null; finishedAt: string | null }>;
}

export interface MonitoringOverview {
  prometheusUp: boolean;
  message: string | null;
  namespace: string;
  cpuCores: number | null;
  memoryBytes: number | null;
  podCount: number | null;
  restartCount: number | null;
  controlPlaneHeapBytes: number | null;
  scrapedAt: string | null;
}

export interface MetricSeries {
  metric: string;
  range: string;
  timestamps: number[];
  values: number[];
  message: string | null;
}

export interface PodView {
  name: string;
  namespace: string;
  phase: string;
  restarts: number;
  node: string;
  images: string[];
  createdAt: string;
  cpu?: string | null;
  memory?: string | null;
}

export interface QualityView {
  available: boolean;
  mode: string;
  projectKey: string;
  qualityGate: string | null;
  coverage: number | null;
  bugs: number | null;
  vulnerabilities: number | null;
  codeSmells: number | null;
  duplications: number | null;
  analyzedAt: string | null;
  url: string | null;
  message: string | null;
}

export interface K8sDeploymentRow {
  name: string;
  namespace: string;
  replicas: number | null;
  readyReplicas: number | null;
  images: string[];
  createdAt: string;
}

export interface K8sServiceRow {
  name: string;
  namespace: string;
  type: string;
  clusterIP: string;
  ports: string[];
  createdAt: string;
}

export interface K8sIngressRow {
  name: string;
  namespace: string;
  hosts: string[];
  createdAt: string;
}

export interface K8sEventRow {
  name: string;
  namespace: string;
  reason: string;
  message: string;
  type: string;
  involvedObject: string;
  createdAt: string;
}

export interface DockerImageRow {
  id: string;
  buildId: string | null;
  repository: string;
  tag: string;
  dockerImageId: string | null;
  sizeBytes: number | null;
  createdAt: string | null;
  source: string | null;
}

export interface DockerImageListing {
  images: DockerImageRow[];
  warning: string | null;
}

export interface DockerContainerRow {
  id: string;
  image: string;
  state: string;
  status: string;
  names: string[];
  createdAt: string | null;
}

export interface HistoryView {
  id: string;
  applicationId: string;
  actorId: string | null;
  action: string;
  summary: string | null;
  createdAt: string;
}

export interface AuditEventView {
  id: string;
  actorId: string | null;
  action: string;
  ipAddress: string | null;
  details: string | null;
  createdAt: string;
}

export interface ReleaseResult {
  name?: string;
  namespace?: string;
  strategy?: string;
  activeTrack?: string;
  canaryWeight?: number | null;
  status?: string;
  message?: string;
  trafficApplied?: boolean;
  image?: string;
  replicas?: number;
  readyReplicas?: number;
  revision?: string;
}

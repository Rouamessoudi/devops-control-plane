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
  timestamps: string[];
  values: number[];
  message: string | null;
}

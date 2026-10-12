import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApplicationView,
  BuildView,
  DeploymentView,
  DockerContainerRow,
  DockerImageListing,
  EnvVarView,
  HistoryView,
  K8sDeploymentRow,
  K8sEventRow,
  K8sIngressRow,
  K8sServiceRow,
  MetricSeries,
  MonitoringOverview,
  PageResponse,
  PipelineView,
  PodView,
  QualityView,
  UserProfile
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  applications(status?: string): Observable<PageResponse<ApplicationView>> {
    let params = new HttpParams().set('page', '0').set('size', '100');
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<ApplicationView>>('/api/applications', { params });
  }

  application(id: string): Observable<ApplicationView> {
    return this.http.get<ApplicationView>(`/api/applications/${id}`);
  }

  createApplication(body: Partial<ApplicationView>): Observable<ApplicationView> {
    return this.http.post<ApplicationView>('/api/applications', body);
  }

  updateApplication(id: string, body: Partial<ApplicationView>): Observable<ApplicationView> {
    return this.http.put<ApplicationView>(`/api/applications/${id}`, body);
  }

  deleteApplication(id: string): Observable<void> {
    return this.http.delete<void>(`/api/applications/${id}`);
  }

  history(id: string): Observable<HistoryView[]> {
    return this.http.get<HistoryView[]>(`/api/applications/${id}/history`);
  }

  env(id: string): Observable<EnvVarView[]> {
    return this.http.get<EnvVarView[]>(`/api/applications/${id}/env`);
  }

  addEnv(id: string, body: EnvVarView): Observable<EnvVarView> {
    return this.http.post<EnvVarView>(`/api/applications/${id}/env`, body);
  }

  deleteEnv(id: string, envId: string): Observable<void> {
    return this.http.delete<void>(`/api/applications/${id}/env/${envId}`);
  }

  deployments(applicationId?: string): Observable<PageResponse<DeploymentView>> {
    let params = new HttpParams().set('page', '0').set('size', '50');
    if (applicationId) {
      params = params.set('applicationId', applicationId);
    }
    return this.http.get<PageResponse<DeploymentView>>('/api/deployments', { params });
  }

  deploy(body: { applicationId: string; imageTag?: string; replicas?: number }): Observable<DeploymentView> {
    return this.http.post<DeploymentView>('/api/deployments', body);
  }

  rollback(id: string): Observable<DeploymentView> {
    return this.http.post<DeploymentView>(`/api/deployments/${id}/rollback`, {});
  }

  revisions(id: string): Observable<DeploymentView[]> {
    return this.http.get<DeploymentView[]>(`/api/deployments/${id}/revisions`);
  }

  builds(applicationId?: string): Observable<BuildView[]> {
    let params = new HttpParams();
    if (applicationId) {
      params = params.set('applicationId', applicationId);
    }
    return this.http.get<BuildView[]>('/api/docker/builds', { params });
  }

  build(applicationId: string, imageTag?: string): Observable<BuildView> {
    return this.http.post<BuildView>('/api/docker/builds', { applicationId, imageTag });
  }

  buildDetails(id: string): Observable<BuildView> {
    return this.http.get<BuildView>(`/api/docker/builds/${id}`);
  }

  images(): Observable<DockerImageListing> {
    return this.http.get<DockerImageListing>('/api/docker/images');
  }

  containers(): Observable<DockerContainerRow[]> {
    return this.http.get<DockerContainerRow[]>('/api/docker/containers');
  }

  containerLogs(id: string): Observable<string> {
    return this.http.get(`/api/docker/containers/${id}/logs`, { responseType: 'text' });
  }

  k8sStatus(): Observable<{ reachable: boolean; namespace: string; message: string | null }> {
    return this.http.get<{ reachable: boolean; namespace: string; message: string | null }>('/api/kubernetes/status');
  }

  k8s(resource: 'pods'): Observable<PodView[]>;
  k8s(resource: 'services'): Observable<K8sServiceRow[]>;
  k8s(resource: 'ingresses'): Observable<K8sIngressRow[]>;
  k8s(resource: 'deployments'): Observable<K8sDeploymentRow[]>;
  k8s(resource: 'events'): Observable<K8sEventRow[]>;
  k8s(resource: 'pods' | 'services' | 'ingresses' | 'deployments' | 'events'): Observable<unknown[]> {
    return this.http.get<unknown[]>(`/api/kubernetes/${resource}`);
  }

  podLogs(name: string): Observable<string> {
    return this.http.get('/api/kubernetes/pods/' + encodeURIComponent(name) + '/logs', { responseType: 'text', params: { tail: '200' } });
  }

  scale(name: string, replicas: number): Observable<unknown> {
    return this.http.post(`/api/kubernetes/deployments/${name}/scale`, { replicas });
  }

  restart(name: string): Observable<unknown> {
    return this.http.post(`/api/kubernetes/deployments/${name}/restart`, {});
  }

  k8sRevisions(name: string): Observable<{ revision: number; image: string; current: boolean }[]> {
    return this.http.get<{ revision: number; image: string; current: boolean }[]>(`/api/kubernetes/deployments/${encodeURIComponent(name)}/revisions`);
  }

  k8sRollback(name: string, revision?: number): Observable<unknown> {
    const params = revision == null ? undefined : { revision };
    return this.http.post(`/api/kubernetes/deployments/${encodeURIComponent(name)}/rollback`, {}, { params });
  }

  metricsServer(): Observable<{ available: boolean; message: string }> {
    return this.http.get<{ available: boolean; message: string }>('/api/kubernetes/metrics-server');
  }

  blueGreen(body: { name: string; activeImage: string; candidateImage: string; replicas: number; containerPort: number; applicationId: string; candidateTrack: 'blue' | 'green' }): Observable<unknown> {
    return this.http.post('/api/kubernetes/releases/blue-green', body);
  }

  switchTrack(name: string, track: 'blue' | 'green'): Observable<unknown> {
    return this.http.post(`/api/kubernetes/releases/${encodeURIComponent(name)}/switch`, {}, { params: { track } });
  }

  canary(body: { name: string; stableImage: string; canaryImage: string; replicas: number; containerPort: number; applicationId: string; canaryWeight: number }): Observable<unknown> {
    return this.http.post('/api/kubernetes/releases/canary', body);
  }

  autoscaler(name: string, body: { minReplicas: number; maxReplicas: number; cpuUtilization: number; memoryUtilization: number; applicationId: string }): Observable<unknown> {
    return this.http.post(`/api/kubernetes/deployments/${encodeURIComponent(name)}/autoscaler`, body);
  }

  pipelines(): Observable<PageResponse<PipelineView>> {
    return this.http.get<PageResponse<PipelineView>>('/api/pipelines', { params: { page: '0', size: '50' } });
  }

  startPipeline(applicationId: string): Observable<PipelineView> {
    return this.http.post<PipelineView>('/api/pipelines', { applicationId });
  }

  pipeline(id: string): Observable<PipelineView> {
    return this.http.get<PipelineView>(`/api/pipelines/${id}`);
  }

  pipelineLogs(id: string): Observable<string> {
    return this.http.get(`/api/pipelines/${id}/logs`, { responseType: 'text' });
  }

  monitoring(): Observable<MonitoringOverview> {
    return this.http.get<MonitoringOverview>('/api/monitoring/overview');
  }

  series(metric: 'cpu' | 'memory' | 'http' | 'errors' | 'latency' | 'clientErrors' | 'podCpu' | 'podMemory' | 'restarts' | 'deployments', range: string): Observable<MetricSeries> {
    return this.http.get<MetricSeries>('/api/monitoring/series', { params: { metric, range } });
  }

  grafana(): Observable<{ url: string; dashboardPath: string }> {
    return this.http.get<{ url: string; dashboardPath: string }>('/api/monitoring/grafana');
  }

  quality(): Observable<QualityView> {
    return this.http.get<QualityView>('/api/pipelines/quality');
  }

  namespaces(): Observable<{ name: string; status: string }[]> {
    return this.http.get<{ name: string; status: string }[]>('/api/kubernetes/namespaces');
  }

  monitorLogs(pod: string): Observable<string> {
    return this.http.get('/api/monitoring/logs', { responseType: 'text', params: { pod, tail: '200' } });
  }

  users(): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>('/api/users');
  }

  updateRoles(id: string, roles: string[]): Observable<UserProfile> {
    return this.http.put<UserProfile>(`/api/users/${id}/roles`, { roles });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`/api/users/${id}`);
  }

  notifications(): Observable<unknown[]> {
    return this.http.get<unknown[]>('/api/notifications');
  }

  audit(path: string): Observable<PageResponse<unknown>> {
    return this.http.get<PageResponse<unknown>>(path, { params: { page: '0', size: '20' } });
  }
}

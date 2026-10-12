import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApplicationView, DeploymentView, K8sDeploymentRow, K8sServiceRow, MetricSeries, MonitoringOverview, PodView } from '../core/models';
import { badgeClass, errorMessage, formatBytes, formatMetric } from '../core/ui';
import { renderLine } from '../shared/line-chart';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Tableau de bord</h1>
          <p class="muted">Indicateurs lus depuis les API. Une valeur absente reste affichée comme telle.</p>
        </div>
        <a mat-flat-button routerLink="/applications/new">Nouvelle application</a>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (loading()) { <p class="muted">Chargement des applications et des déploiements…</p> }
      <div class="grid">
        <article class="stat"><span class="muted">Applications</span><strong>{{ apps().length }}</strong></article>
        <article class="stat"><span class="muted">Actives</span><strong>{{ countStatus(['RUNNING', 'READY']) }}</strong></article>
        <article class="stat"><span class="muted">Déploiements réussis</span><strong>{{ deployCount('SUCCEEDED') }}</strong></article>
        <article class="stat"><span class="muted">Déploiements échoués</span><strong>{{ deployCount('FAILED') }}</strong></article>
        <article class="stat"><span class="muted">Pods Kubernetes</span><strong>{{ podCount() }}</strong></article>
        <article class="stat"><span class="muted">Services Kubernetes</span><strong>{{ serviceCount() }}</strong></article>
        <article class="stat"><span class="muted">CPU Prometheus</span><strong>{{ format(metrics()?.cpuCores) }}</strong></article>
        <article class="stat"><span class="muted">Mémoire JVM</span><strong>{{ bytes(metrics()?.memoryBytes) }}</strong></article>
      </div>
      @if (metrics()?.message) { <p class="muted">{{ metrics()?.message }}</p> }
      <div class="split">
        <article class="panel">
          <h2>CPU réel</h2>
          <p class="muted">Série Actuator <code>process_cpu_usage</code> sur 1 h. Elle ne contient pas le CPU des Pods.</p>
          @if (cpuMessage()) { <p class="empty">{{ cpuMessage() }}</p> }
          <div class="chart-box"><canvas #cpu></canvas></div>
        </article>
        <article class="panel">
          <h2>Plateforme</h2>
          <ul class="status-list">
            <li>
              @if (cluster() === null) { <span class="badge">…</span> }
              @else { <span [class]="'badge ' + (cluster() ? 'ok' : 'bad')">{{ cluster() ? 'Joignable' : 'Injoignable' }}</span> }
              Kubernetes {{ namespace() || '—' }}
            </li>
            <li>
              @if (!metrics()) { <span class="badge">…</span> }
              @else { <span [class]="'badge ' + (metrics()!.prometheusUp ? 'ok' : 'bad')">{{ metrics()!.prometheusUp ? 'Joignable' : 'Absent' }}</span> }
              Prometheus
            </li>
            <li><span class="badge">{{ format(metrics()?.podCount) }}</span> Pods Kubernetes dans Prometheus</li>
            <li><span class="badge">{{ bytes(metrics()?.controlPlaneHeapBytes) }}</span> Heap JVM du control plane</li>
          </ul>
        </article>
      </div>
      <article class="panel">
        <h2>Charges Kubernetes</h2>
        @if (!workloadsLoaded()) { <p class="muted">Chargement des Deployments…</p> }
        @else if (!workloads().length) { <p class="empty">Aucun Deployment renvoyé par l’API Kubernetes.</p> }
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>Nom</th><th>Namespace</th><th>Prêts</th><th>Image</th></tr></thead>
            <tbody>
              @for (item of workloads(); track item.name) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td>{{ item.namespace }}</td>
                  <td>{{ item.readyReplicas ?? 0 }}/{{ item.replicas ?? 0 }}</td>
                  <td>{{ item.images[0] || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </article>
      <article class="panel">
        <h2>Activité récente</h2>
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>Statut</th><th>Image</th><th>Déclencheur</th><th>Date</th></tr></thead>
            <tbody>
              @for (item of deployments(); track item.id) {
                <tr>
                  <td><span [class]="'badge ' + badge(item.status)">{{ item.status }}</span></td>
                  <td>{{ item.fullImage }}</td>
                  <td>{{ item.trigger }}</td>
                  <td>{{ item.createdAt }}</td>
                </tr>
              } @empty { <tr><td colspan="4">Aucun déploiement enregistré.</td></tr> }
            </tbody>
          </table>
        </div>
      </article>
    </section>
  `
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  readonly apps = signal<ApplicationView[]>([]);
  readonly deployments = signal<DeploymentView[]>([]);
  readonly workloads = signal<K8sDeploymentRow[]>([]);
  readonly workloadsLoaded = signal(false);
  readonly metrics = signal<MonitoringOverview | null>(null);
  readonly cpuSeries = signal<MetricSeries | null>(null);
  readonly cpuMessage = signal('');
  readonly podCount = signal('—');
  readonly serviceCount = signal('—');
  readonly cluster = signal<boolean | null>(null);
  readonly namespace = signal('');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly badge = badgeClass;
  readonly format = formatMetric;
  readonly bytes = formatBytes;
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('cpu');

  constructor() {
    effect(() => {
      const element = this.canvas()?.nativeElement;
      const series = this.cpuSeries();
      if (!element || !series || !series.values.length) {
        return;
      }
      renderLine(element, 'CPU', series.timestamps, series.values, '#3ee0b0');
    });
    forkJoin({
      apps: this.api.applications(),
      deployments: this.api.deployments()
    }).subscribe({
      next: ({ apps, deployments }) => {
        this.apps.set(apps.items);
        this.deployments.set(deployments.items);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
    this.api.monitoring().subscribe({
      next: (overview) => this.metrics.set(overview),
      error: () => this.metrics.set(null)
    });
    this.api.k8s('pods').subscribe({
      next: (pods: PodView[]) => this.podCount.set(String(pods.length)),
      error: () => this.podCount.set('indisponible')
    });
    this.api.k8s('services').subscribe({
      next: (services: K8sServiceRow[]) => this.serviceCount.set(String(services.length)),
      error: () => this.serviceCount.set('indisponible')
    });
    this.api.k8s('deployments').subscribe({
      next: (rows) => { this.workloads.set(rows); this.workloadsLoaded.set(true); },
      error: () => { this.workloads.set([]); this.workloadsLoaded.set(true); }
    });
    this.api.k8sStatus().subscribe({
      next: (status) => {
        this.cluster.set(status.reachable);
        this.namespace.set(status.namespace);
      },
      error: () => this.cluster.set(false)
    });
    this.api.series('cpu', '1h').subscribe({
      next: (series) => {
        this.cpuSeries.set(series);
        this.cpuMessage.set(series.values.length ? '' : (series.message || 'Prometheus n’a renvoyé aucun échantillon CPU.'));
      },
      error: (err: unknown) => this.cpuMessage.set(errorMessage(err))
    });
  }

  countStatus(values: string[]): number {
    return this.apps().filter((app) => values.includes(app.status)).length;
  }

  deployCount(status: string): number {
    return this.deployments().filter((item) => item.status === status).length;
  }
}

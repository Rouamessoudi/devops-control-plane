import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { MetricSeries, MonitoringOverview } from '../core/models';
import { errorMessage, formatBytes, formatMetric } from '../core/ui';
import { grafanaEmbedUrl } from '../core/grafana-url';
import { clearLine, renderLine } from '../shared/line-chart';

@Component({
  selector: 'app-monitoring',
  imports: [MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Monitoring</h1>
          <p class="muted">Séries Prometheus uniquement. Une série vide n’est pas remplacée par des zéros.</p>
        </div>
        <div class="row-actions">
          @for (item of ranges; track item) {
            <button mat-stroked-button type="button" (click)="selectRange(item)">{{ item }}</button>
          }
        </div>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (overview(); as data) {
        <div class="grid">
          <article class="stat"><span class="muted">Prometheus</span><strong>{{ data.prometheusUp ? 'joignable' : 'erreur' }}</strong></article>
          <article class="stat"><span class="muted">CPU (série cpu)</span><strong>{{ format(data.cpuCores) }}</strong></article>
          <article class="stat"><span class="muted">Mémoire (série memory)</span><strong>{{ bytes(data.memoryBytes) }}</strong></article>
          <article class="stat"><span class="muted">Pods Kubernetes (Prometheus)</span><strong>{{ format(data.podCount) }}</strong></article>
          <article class="stat"><span class="muted">Redémarrages Kubernetes</span><strong>{{ format(data.restartCount) }}</strong></article>
          <article class="stat"><span class="muted">Heap JVM</span><strong>{{ bytes(data.controlPlaneHeapBytes) }}</strong></article>
        </div>
        @if (data.message) { <p class="muted">{{ data.message }}</p> }
      }
      <h2>Métriques JVM / actuator</h2>
      <p class="muted">CPU, mémoire, requêtes, erreurs et latence viennent du job Actuator devops-control-plane. Ce ne sont pas les métriques des Pods.</p>
      <div class="split">
        <article class="panel">
          <h3>CPU</h3>
          @if (cpuNote()) { <p class="empty">{{ cpuNote() }}</p> }
          <div class="chart-box"><canvas #cpu></canvas></div>
        </article>
        <article class="panel">
          <h3>Mémoire</h3>
          @if (memoryNote()) { <p class="empty">{{ memoryNote() }}</p> }
          <div class="chart-box"><canvas #memory></canvas></div>
        </article>
      </div>
      <h2>Trafic HTTP des applications</h2>
      <p class="muted">Ces séries viennent de Micrometer. Elles ne décrivent pas les Pods Kubernetes.</p>
      <div class="split">
        <article class="panel">
          <h3>Requêtes</h3>
          @if (httpNote()) { <p class="empty">{{ httpNote() }}</p> }
          <div class="chart-box"><canvas #http></canvas></div>
        </article>
        <article class="panel">
          <h3>Erreurs 5xx</h3>
          @if (errorsNote()) { <p class="empty">{{ errorsNote() }}</p> }
          <div class="chart-box"><canvas #errors></canvas></div>
        </article>
      </div>
      <div class="split">
        <article class="panel">
          <h3>Erreurs 4xx</h3>
          @if (clientErrorsNote()) { <p class="empty">{{ clientErrorsNote() }}</p> }
          <div class="chart-box"><canvas #clientErrors></canvas></div>
        </article>
        <article class="panel">
          <h3>Latence</h3>
          @if (latencyNote()) { <p class="empty">{{ latencyNote() }}</p> }
          <div class="chart-box"><canvas #latency></canvas></div>
        </article>
      </div>
      <h2>Métriques Kubernetes</h2>
      <p class="muted">CPU et RAM des Pods viennent de cAdvisor. Les redémarrages et les replicas viennent de kube-state-metrics. Une série absente reste vide.</p>
      <div class="split">
        <article class="panel">
          <h3>CPU des Pods</h3>
          @if (podCpuNote()) { <p class="empty">{{ podCpuNote() }}</p> }
          <div class="chart-box"><canvas #podCpu></canvas></div>
        </article>
        <article class="panel">
          <h3>RAM des Pods</h3>
          @if (podMemoryNote()) { <p class="empty">{{ podMemoryNote() }}</p> }
          <div class="chart-box"><canvas #podMemory></canvas></div>
        </article>
      </div>
      <div class="split">
        <article class="panel">
          <h3>Redémarrages</h3>
          @if (restartsNote()) { <p class="empty">{{ restartsNote() }}</p> }
          <div class="chart-box"><canvas #restarts></canvas></div>
        </article>
        <article class="panel">
          <h3>Replicas disponibles</h3>
          @if (deploymentsNote()) { <p class="empty">{{ deploymentsNote() }}</p> }
          <div class="chart-box"><canvas #deployments></canvas></div>
        </article>
      </div>
      <article class="panel">
        <h2>Grafana</h2>
        <p class="muted">Le tableau intégré utilise le Viewer anonyme déjà configuré. L’édition reste réservée au compte administrateur Grafana.</p>
        @if (embedUrl()) { <iframe #grafanaPane class="grafana-frame" title="Tableau de bord Grafana"></iframe> }
        @if (grafana()) { <p><a [href]="grafana()!.url + grafana()!.dashboardPath" target="_blank" rel="noopener">Ouvrir Grafana</a></p> }
      </article>
      <article class="panel">
        <h2>Santé observée</h2>
        <ul class="status-list">
          <li><span [class]="'badge ' + (overview()?.prometheusUp ? 'ok' : 'bad')">{{ overview()?.prometheusUp ? 'UP' : 'DOWN' }}</span> Prometheus</li>
          <li><span class="badge">{{ overview()?.namespace || '—' }}</span> Namespace des requêtes Kubernetes</li>
          <li><span class="badge">{{ overview()?.scrapedAt || '—' }}</span> Dernier scrape</li>
        </ul>
      </article>
    </section>
  `
})
export class MonitoringPage {
  private readonly api = inject(ApiService);
  private readonly grafanaPane = viewChild<ElementRef<HTMLIFrameElement>>('grafanaPane');
  readonly ranges = ['15m', '1h', '6h', '24h'];
  readonly range = signal('1h');
  readonly overview = signal<MonitoringOverview | null>(null);
  readonly grafana = signal<{ url: string; dashboardPath: string } | null>(null);
  readonly error = signal('');
  readonly cpuSeries = signal<MetricSeries | null>(null);
  readonly memorySeries = signal<MetricSeries | null>(null);
  readonly httpSeries = signal<MetricSeries | null>(null);
  readonly errorsSeries = signal<MetricSeries | null>(null);
  readonly latencySeries = signal<MetricSeries | null>(null);
  readonly cpuNote = signal('');
  readonly memoryNote = signal('');
  readonly httpNote = signal('');
  readonly errorsNote = signal('');
  readonly latencyNote = signal('');
  readonly clientErrorsSeries = signal<MetricSeries | null>(null);
  readonly podCpuSeries = signal<MetricSeries | null>(null);
  readonly podMemorySeries = signal<MetricSeries | null>(null);
  readonly restartsSeries = signal<MetricSeries | null>(null);
  readonly deploymentsSeries = signal<MetricSeries | null>(null);
  readonly clientErrorsNote = signal('');
  readonly podCpuNote = signal('');
  readonly podMemoryNote = signal('');
  readonly restartsNote = signal('');
  readonly deploymentsNote = signal('');
  readonly format = formatMetric;
  readonly bytes = formatBytes;
  private readonly cpu = viewChild<ElementRef<HTMLCanvasElement>>('cpu');
  private readonly memory = viewChild<ElementRef<HTMLCanvasElement>>('memory');
  private readonly http = viewChild<ElementRef<HTMLCanvasElement>>('http');
  private readonly errors = viewChild<ElementRef<HTMLCanvasElement>>('errors');
  private readonly latency = viewChild<ElementRef<HTMLCanvasElement>>('latency');
  private readonly clientErrors = viewChild<ElementRef<HTMLCanvasElement>>('clientErrors');
  private readonly podCpu = viewChild<ElementRef<HTMLCanvasElement>>('podCpu');
  private readonly podMemory = viewChild<ElementRef<HTMLCanvasElement>>('podMemory');
  private readonly restarts = viewChild<ElementRef<HTMLCanvasElement>>('restarts');
  private readonly deployments = viewChild<ElementRef<HTMLCanvasElement>>('deployments');

  constructor() {
    effect(() => {
      const pane = this.grafanaPane()?.nativeElement;
      const url = this.embedUrl();
      if (!pane) {
        return;
      }
      if (url) {
        pane.src = url;
      } else {
        pane.removeAttribute('src');
      }
    });
    effect(() => this.paint(this.cpu()?.nativeElement, this.cpuSeries(), 'CPU', '#3ee0b0'));
    effect(() => this.paint(this.memory()?.nativeElement, this.memorySeries(), 'Mémoire', '#7aa2ff'));
    effect(() => this.paint(this.http()?.nativeElement, this.httpSeries(), 'Requêtes', '#f5c16c'));
    effect(() => this.paint(this.errors()?.nativeElement, this.errorsSeries(), 'Erreurs', '#ff6b6b'));
    effect(() => this.paint(this.latency()?.nativeElement, this.latencySeries(), 'Latence', '#c084fc'));
    effect(() => this.paint(this.clientErrors()?.nativeElement, this.clientErrorsSeries(), '4xx', '#fb923c'));
    effect(() => this.paint(this.podCpu()?.nativeElement, this.podCpuSeries(), 'CPU Pods', '#2dd4bf'));
    effect(() => this.paint(this.podMemory()?.nativeElement, this.podMemorySeries(), 'RAM Pods', '#38bdf8'));
    effect(() => this.paint(this.restarts()?.nativeElement, this.restartsSeries(), 'Redémarrages', '#f472b6'));
    effect(() => this.paint(this.deployments()?.nativeElement, this.deploymentsSeries(), 'Replicas', '#a3e635'));
    this.api.monitoring().subscribe({
      next: (data) => this.overview.set(data),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
    this.api.grafana().subscribe({ next: (link) => this.grafana.set(link), error: () => this.grafana.set(null) });
    this.loadSeries();
  }

  selectRange(range: string): void {
    this.range.set(range);
    this.loadSeries();
  }

  private loadSeries(): void {
    const range = this.range();
    this.read('cpu', range, this.cpuSeries, this.cpuNote);
    this.read('memory', range, this.memorySeries, this.memoryNote);
    this.read('http', range, this.httpSeries, this.httpNote);
    this.read('errors', range, this.errorsSeries, this.errorsNote);
    this.read('latency', range, this.latencySeries, this.latencyNote);
    this.read('clientErrors', range, this.clientErrorsSeries, this.clientErrorsNote);
    this.read('podCpu', range, this.podCpuSeries, this.podCpuNote);
    this.read('podMemory', range, this.podMemorySeries, this.podMemoryNote);
    this.read('restarts', range, this.restartsSeries, this.restartsNote);
    this.read('deployments', range, this.deploymentsSeries, this.deploymentsNote);
  }

  embedUrl(): string | null {
    const link = this.grafana();
    return grafanaEmbedUrl(link?.url, link?.dashboardPath);
  }

  private read(metric: 'cpu' | 'memory' | 'http' | 'errors' | 'latency' | 'clientErrors' | 'podCpu' | 'podMemory' | 'restarts' | 'deployments', range: string, target: { set: (value: MetricSeries | null) => void }, note: { set: (value: string) => void }): void {
    this.api.series(metric, range).subscribe({
      next: (series) => {
        target.set(series.values.length ? series : null);
        note.set(series.values.length ? '' : (series.message || 'Prometheus n’a renvoyé aucun échantillon.'));
      },
      error: (err: unknown) => {
        target.set(null);
        note.set(errorMessage(err));
      }
    });
  }

  private paint(canvas: HTMLCanvasElement | undefined, series: MetricSeries | null, label: string, color: string): void {
    if (!canvas) {
      return;
    }
    if (!series?.values.length) {
      clearLine(canvas);
      return;
    }
    renderLine(canvas, label, series.timestamps, series.values, color);
  }
}

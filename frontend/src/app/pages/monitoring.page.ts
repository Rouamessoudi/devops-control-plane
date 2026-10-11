import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Chart, LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip } from 'chart.js';
import { ApiService } from '../core/api.service';
import { MonitoringOverview } from '../core/models';
import { errorMessage } from '../core/ui';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip);

@Component({
  selector: 'app-monitoring',
  template: `
    <section class="page">
      <h1>Monitoring</h1>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (overview(); as data) {
        <div class="grid">
          <article class="stat"><span class="muted">Prometheus</span><strong>{{ data.prometheusUp ? 'joignable' : 'erreur' }}</strong></article>
          <article class="stat"><span class="muted">CPU</span><strong>{{ data.cpuCores ?? '—' }}</strong></article>
          <article class="stat"><span class="muted">RAM</span><strong>{{ data.memoryBytes ?? '—' }}</strong></article>
          <article class="stat"><span class="muted">Pods</span><strong>{{ data.podCount ?? '—' }}</strong></article>
          <article class="stat"><span class="muted">Redémarrages</span><strong>{{ data.restartCount ?? '—' }}</strong></article>
          <article class="stat"><span class="muted">Heap control plane</span><strong>{{ data.controlPlaneHeapBytes ?? '—' }}</strong></article>
        </div>
        @if (data.message) { <p class="muted">{{ data.message }}</p> }
      }
      <article class="panel">
        <h2>CPU réel (Prometheus)</h2>
        <div class="chart-box"><canvas #cpu></canvas></div>
      </article>
      @if (grafana()) { <p><a [href]="grafana()!.url + grafana()!.dashboardPath" target="_blank" rel="noopener">Ouvrir Grafana</a></p> }
    </section>
  `
})
export class MonitoringPage {
  private readonly api = inject(ApiService);
  readonly overview = signal<MonitoringOverview | null>(null);
  readonly grafana = signal<{ url: string; dashboardPath: string } | null>(null);
  readonly error = signal('');
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('cpu');

  constructor() {
    this.api.monitoring().subscribe({
      next: (data) => this.overview.set(data),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
    this.api.grafana().subscribe({ next: (link) => this.grafana.set(link), error: () => this.grafana.set(null) });
    this.api.series('cpu', '1h').subscribe({
      next: (series) => this.draw(series.timestamps, series.values, series.message),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  private draw(labels: string[], values: number[], message: string | null): void {
    const element = this.canvas()?.nativeElement;
    if (!element) {
      return;
    }
    if (!values.length) {
      this.error.set(message || 'Prometheus n’a renvoyé aucune série CPU.');
      return;
    }
    new Chart(element, {
      type: 'line',
      data: { labels, datasets: [{ label: 'CPU', data: values, borderColor: '#3ee0b0', tension: 0.2 }] },
      options: { responsive: true, maintainAspectRatio: false }
    });
  }
}

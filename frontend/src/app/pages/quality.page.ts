import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { QualityView } from '../core/models';
import { errorMessage, formatMetric } from '../core/ui';

@Component({
  selector: 'app-quality',
  imports: [MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Qualité</h1>
          <p class="muted">Les chiffres viennent de SonarQube. Un tiret signifie que la mesure n’a pas été renvoyée.</p>
        </div>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (loading()) { <p class="muted">Chargement…</p> }
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (quality(); as item) {
        @if (!item.available) {
          <p class="empty">SonarQube est injoignable. Aucun résultat n’est affiché à sa place. {{ item.message }}</p>
        } @else if (item.qualityGate === 'OK') {
          <p class="empty">Quality Gate {{ item.qualityGate }}.</p>
        } @else if (item.qualityGate) {
          <p class="error-banner">Quality Gate {{ item.qualityGate }}. {{ item.message }}</p>
        } @else {
          <p class="empty">SonarQube répond, mais aucun Quality Gate n’est disponible. {{ item.message }}</p>
        }
        <div class="grid">
          <article class="stat"><span class="muted">Mode</span><strong>{{ item.mode }}</strong></article>
          <article class="stat"><span class="muted">Projet</span><strong>{{ item.projectKey }}</strong></article>
          <article class="stat"><span class="muted">Quality Gate</span><strong>{{ item.qualityGate || '—' }}</strong></article>
          <article class="stat"><span class="muted">Couverture</span><strong>{{ metric(item.coverage) }}</strong></article>
          <article class="stat"><span class="muted">Bugs</span><strong>{{ metric(item.bugs) }}</strong></article>
          <article class="stat"><span class="muted">Vulnérabilités</span><strong>{{ metric(item.vulnerabilities) }}</strong></article>
          <article class="stat"><span class="muted">Code smells</span><strong>{{ metric(item.codeSmells) }}</strong></article>
          <article class="stat"><span class="muted">Duplications</span><strong>{{ percent(item.duplications) }}</strong></article>
          <article class="stat"><span class="muted">Dernière analyse</span><strong>{{ item.analyzedAt || '—' }}</strong></article>
        </div>
        @if (item.url) {
          <p><a [href]="item.url" target="_blank" rel="noopener">Ouvrir SonarQube</a></p>
        }
        <article class="panel">
          <h2>Pipeline</h2>
          <p class="muted">En mode off, les étapes sonar-analysis et quality-gate sont ignorées et le déploiement continue. En mode required, un serveur absent ou un gate en échec bloque le déploiement.</p>
        </article>
      }
    </section>
  `
})
export class QualityPage {
  private readonly api = inject(ApiService);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly quality = signal<QualityView | null>(null);
  readonly metric = formatMetric;

  percent(value: number | null): string {
    return value == null || Number.isNaN(value) ? '—' : `${formatMetric(value)} %`;
  }

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.quality().subscribe({
      next: (item) => {
        this.quality.set(item);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.quality.set(null);
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}

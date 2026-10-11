import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApplicationView, DeploymentView, MonitoringOverview } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Tableau de bord</h1>
          <p class="muted">Indicateurs lus depuis les API, sans valeur de substitution.</p>
        </div>
        <a mat-flat-button routerLink="/applications/new">Nouvelle application</a>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (loading()) { <p>Chargement…</p> }
      <div class="grid">
        <article class="stat"><span class="muted">Applications</span><strong>{{ apps().length }}</strong></article>
        <article class="stat"><span class="muted">Actives</span><strong>{{ countStatus(['RUNNING', 'READY']) }}</strong></article>
        <article class="stat"><span class="muted">En erreur</span><strong>{{ countStatus(['FAILED', 'ERROR']) }}</strong></article>
        <article class="stat"><span class="muted">Déploiements réussis</span><strong>{{ deployCount('SUCCEEDED') }}</strong></article>
        <article class="stat"><span class="muted">Déploiements échoués</span><strong>{{ deployCount('FAILED') }}</strong></article>
        <article class="stat"><span class="muted">Pods</span><strong>{{ pods() }}</strong></article>
        <article class="stat"><span class="muted">CPU</span><strong>{{ metrics()?.cpuCores ?? '—' }}</strong></article>
        <article class="stat"><span class="muted">Mémoire JVM (octets)</span><strong>{{ metrics()?.memoryBytes ?? '—' }}</strong></article>
      </div>
      @if (metrics()?.message) { <p class="muted">{{ metrics()?.message }}</p> }
      <article class="panel">
        <h2>Derniers déploiements</h2>
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
  readonly metrics = signal<MonitoringOverview | null>(null);
  readonly pods = signal<string>('—');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly badge = badgeClass;

  constructor() {
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
      next: (pods) => this.pods.set(String(pods.length)),
      error: () => this.pods.set('indisponible')
    });
  }

  countStatus(values: string[]): number {
    return this.apps().filter((app) => values.includes(app.status)).length;
  }

  deployCount(status: string): number {
    return this.deployments().filter((item) => item.status === status).length;
  }
}

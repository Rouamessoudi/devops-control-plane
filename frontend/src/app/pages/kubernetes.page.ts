import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-kubernetes',
  imports: [MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <h1>{{ title() }}</h1>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (status()) { <p class="muted">Cluster {{ status()!.reachable ? 'joignable' : 'injoignable' }} · namespace {{ status()!.namespace }}. {{ status()!.message }}</p> }
      <article class="panel">
        <pre class="log">{{ body() }}</pre>
      </article>
      @if (view() === 'deployments') {
        <p class="muted">Les actions ne concernent que les déploiements marqués par la plateforme.</p>
      }
    </section>
  `
})
export class KubernetesPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly view = signal(this.route.snapshot.data['view'] as string);
  readonly title = signal('Kubernetes');
  readonly body = signal('Chargement…');
  readonly error = signal('');
  readonly status = signal<{ reachable: boolean; namespace: string; message: string | null } | null>(null);

  constructor() {
    const titles: Record<string, string> = {
      overview: 'Kubernetes', pods: 'Pods', services: 'Services', ingresses: 'Ingress', deployments: 'Deployments'
    };
    this.title.set(titles[this.view()] ?? 'Kubernetes');
    this.load();
  }

  load(): void {
    this.api.k8sStatus().subscribe({ next: (status) => this.status.set(status), error: () => this.status.set(null) });
    const resource = this.view() === 'overview' ? 'pods' : this.view() as 'pods' | 'services' | 'ingresses' | 'deployments';
    const call = this.view() === 'overview'
      ? this.api.k8s('events')
      : this.api.k8s(resource);
    call.subscribe({
      next: (items) => this.body.set(JSON.stringify(items, null, 2)),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }
}

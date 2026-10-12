import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { K8sDeploymentRow, K8sEventRow, K8sIngressRow, K8sServiceRow, PodView } from '../core/models';
import { badgeClass, errorMessage, formatAge } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-kubernetes',
  imports: [FormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>{{ title() }}</h1>
          @if (status()) {
            <p class="muted">Cluster {{ status()!.reachable ? 'joignable' : 'injoignable' }} · namespace {{ status()!.namespace }}. {{ status()!.message }}</p>
          }
        </div>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (loading()) { <p class="muted">Chargement…</p> }
      @if (view() !== 'namespaces') {
        <div class="toolbar">
          <mat-form-field appearance="outline">
            <mat-label>Rechercher</mat-label>
            <input matInput [(ngModel)]="query" placeholder="nom ou namespace">
          </mat-form-field>
        </div>
      }

      @if (view() === 'overview' || view() === 'pods') {
        <article class="panel table-wrap">
          <h2>Pods</h2>
          <p class="muted">CPU et RAM viennent de metrics.k8s.io. Un tiret signifie que Metrics Server n’a pas renvoyé cette valeur.</p>
          <table class="data">
            <thead><tr><th>Nom</th><th>Namespace</th><th>Statut</th><th>Restarts</th><th>CPU</th><th>RAM</th><th>Image</th><th>Âge</th><th></th></tr></thead>
            <tbody>
              @for (item of filteredPods(); track item.name) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td>{{ item.namespace }}</td>
                  <td><span [class]="'badge ' + badge(item.phase)">{{ item.phase }}</span></td>
                  <td>{{ item.restarts }}</td>
                  <td>{{ item.cpu || '—' }}</td>
                  <td>{{ item.memory || '—' }}</td>
                  <td>{{ item.images[0] || '—' }}</td>
                  <td>{{ age(item.createdAt) }}</td>
                  <td><button mat-button type="button" (click)="selectPod(item)">Détails</button></td>
                </tr>
              } @empty { <tr><td colspan="9">Aucun Pod ne correspond.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (view() === 'services') {
        <article class="panel table-wrap">
          <h2>Services</h2>
          <table class="data">
            <thead><tr><th>Nom</th><th>Namespace</th><th>Type</th><th>Cluster IP</th><th>Ports</th><th>Âge</th></tr></thead>
            <tbody>
              @for (item of filteredServices(); track item.name) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td>{{ item.namespace }}</td>
                  <td>{{ item.type }}</td>
                  <td>{{ item.clusterIP }}</td>
                  <td>{{ item.ports.join(', ') || '—' }}</td>
                  <td>{{ age(item.createdAt) }}</td>
                </tr>
              } @empty { <tr><td colspan="6">Aucun Service ne correspond.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (view() === 'ingresses') {
        <article class="panel table-wrap">
          <h2>Ingress</h2>
          <table class="data">
            <thead><tr><th>Nom</th><th>Namespace</th><th>Hôtes</th><th>Âge</th></tr></thead>
            <tbody>
              @for (item of filteredIngresses(); track item.name) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td>{{ item.namespace }}</td>
                  <td>{{ item.hosts.join(', ') || '—' }}</td>
                  <td>{{ age(item.createdAt) }}</td>
                </tr>
              } @empty { <tr><td colspan="4">Aucun Ingress ne correspond.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (view() === 'deployments') {
        <article class="panel table-wrap">
          <h2>Deployments</h2>
          <p class="muted">Le scale et le redémarrage demandent une confirmation. Ils ne sont pas lancés automatiquement.</p>
          <table class="data">
            <thead><tr><th>Nom</th><th>Namespace</th><th>Statut</th><th>Replicas</th><th>Image</th><th>Âge</th><th></th></tr></thead>
            <tbody>
              @for (item of filteredDeployments(); track item.name) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td>{{ item.namespace }}</td>
                  <td><span [class]="'badge ' + badge(ready(item) ? 'READY' : 'PENDING')">{{ item.readyReplicas ?? 0 }}/{{ item.replicas ?? 0 }}</span></td>
                  <td>{{ item.replicas ?? 0 }}</td>
                  <td>{{ item.images[0] || '—' }}</td>
                  <td>{{ age(item.createdAt) }}</td>
                  <td><button mat-button type="button" (click)="selectDeployment(item)">Actions</button></td>
                </tr>
              } @empty { <tr><td colspan="7">Aucun Deployment ne correspond.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (view() === 'namespaces') {
        <article class="panel">
          <h2>Namespaces</h2>
          <p class="muted">Liste lue sur le cluster. Le namespace de travail du plan de contrôle est {{ status()?.namespace || '—' }}.</p>
          <table class="data">
            <thead><tr><th>Nom</th><th>Statut</th></tr></thead>
            <tbody>
              @for (item of namespaces(); track item.name) {
                <tr><td>{{ item.name }}</td><td>{{ item.status || '—' }}</td></tr>
              } @empty { <tr><td colspan="2">Aucun namespace renvoyé.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (view() === 'overview' || view() === 'pods' || view() === 'deployments') {
        <article class="panel table-wrap">
          <h2>Événements</h2>
          <table class="data">
            <thead><tr><th>Type</th><th>Raison</th><th>Objet</th><th>Message</th><th>Âge</th></tr></thead>
            <tbody>
              @for (item of filteredEvents(); track item.name) {
                <tr>
                  <td><span [class]="'badge ' + badge(item.type === 'Warning' ? 'FAILED' : 'READY')">{{ item.type }}</span></td>
                  <td>{{ item.reason }}</td>
                  <td>{{ item.involvedObject }}</td>
                  <td class="wrap">{{ item.message }}</td>
                  <td>{{ age(item.createdAt) }}</td>
                </tr>
              } @empty { <tr><td colspan="5">Aucun événement.</td></tr> }
            </tbody>
          </table>
        </article>
      }

      @if (pod(); as item) {
        <article class="panel">
          <h2>{{ item.name }}</h2>
          <dl class="facts">
            <div><dt>Namespace</dt><dd>{{ item.namespace }}</dd></div>
            <div><dt>Phase</dt><dd>{{ item.phase }}</dd></div>
            <div><dt>Nœud</dt><dd>{{ item.node || '—' }}</dd></div>
            <div><dt>Images</dt><dd>{{ item.images.join(', ') || '—' }}</dd></div>
          </dl>
          <button mat-stroked-button type="button" (click)="logs(item.name)">Logs</button>
          @if (detailLog()) { <pre class="log">{{ detailLog() }}</pre> }
          <h3>Événements liés</h3>
          @for (event of related(item.name); track event.name) {
            <p>{{ event.reason }} — {{ event.message }}</p>
          } @empty { <p class="empty">Aucun événement ne cite ce nom.</p> }
        </article>
      }

      @if (deployment(); as item) {
        <article class="panel">
          <h2>{{ item.name }}</h2>
          <dl class="facts">
            <div><dt>Namespace</dt><dd>{{ item.namespace }}</dd></div>
            <div><dt>Replicas</dt><dd>{{ item.readyReplicas ?? 0 }} prêts / {{ item.replicas ?? 0 }} demandés</dd></div>
            <div><dt>Images</dt><dd>{{ item.images.join(', ') || '—' }}</dd></div>
          </dl>
          <h3>Historique des révisions</h3>
          <table class="data">
            <thead><tr><th>Révision</th><th>Image</th><th>Courante</th></tr></thead>
            <tbody>
              @for (revision of revisions(); track revision.revision) {
                <tr>
                  <td>{{ revision.revision }}</td>
                  <td>{{ revision.image || '—' }}</td>
                  <td>{{ revision.current ? 'oui' : 'non' }}</td>
                </tr>
              } @empty { <tr><td colspan="3">Aucune révision renvoyée.</td></tr> }
            </tbody>
          </table>
          <div class="row-actions">
            <mat-form-field appearance="outline"><mat-label>Replicas</mat-label><input matInput type="number" [(ngModel)]="replicas"></mat-form-field>
            <button mat-stroked-button type="button" (click)="confirmScale(item.name)">Changer l’échelle</button>
            <button mat-stroked-button type="button" (click)="confirmRestart(item.name)">Redémarrer</button>
          </div>
        </article>
      }
    </section>
  `
})
export class KubernetesPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  readonly view = signal(this.route.snapshot.data['view'] as string);
  readonly title = signal('Kubernetes');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly status = signal<{ reachable: boolean; namespace: string; message: string | null } | null>(null);
  readonly pods = signal<PodView[]>([]);
  readonly services = signal<K8sServiceRow[]>([]);
  readonly ingresses = signal<K8sIngressRow[]>([]);
  readonly deployments = signal<K8sDeploymentRow[]>([]);
  readonly events = signal<K8sEventRow[]>([]);
  readonly namespaces = signal<{ name: string; status: string }[]>([]);
  readonly revisions = signal<{ revision: number; image: string; current: boolean }[]>([]);
  readonly pod = signal<PodView | null>(null);
  readonly deployment = signal<K8sDeploymentRow | null>(null);
  readonly detailLog = signal('');
  query = '';
  replicas = 1;
  readonly badge = badgeClass;
  readonly age = formatAge;

  constructor() {
    const titles: Record<string, string> = {
      overview: 'Kubernetes',
      pods: 'Pods',
      services: 'Services',
      ingresses: 'Ingress',
      deployments: 'Deployments',
      namespaces: 'Namespaces'
    };
    this.title.set(titles[this.view()] ?? 'Kubernetes');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.k8sStatus().subscribe({ next: (status) => this.status.set(status), error: () => this.status.set(null) });
    const view = this.view();
    if (view === 'namespaces') {
      this.api.namespaces().subscribe({
        next: (items) => { this.namespaces.set(items); this.loading.set(false); },
        error: (err: unknown) => this.fail(err)
      });
      return;
    }
    if (view === 'overview' || view === 'pods') {
      this.api.k8s('pods').subscribe({
        next: (items) => { this.pods.set(items); this.loading.set(false); },
        error: (err: unknown) => this.fail(err)
      });
    }
    if (view === 'overview' || view === 'pods' || view === 'deployments') {
      this.api.k8s('events').subscribe({
        next: (items) => this.events.set(items),
        error: (err: unknown) => this.fail(err)
      });
    }
    if (view === 'services') {
      this.api.k8s('services').subscribe({
        next: (items) => { this.services.set(items); this.loading.set(false); },
        error: (err: unknown) => this.fail(err)
      });
    }
    if (view === 'ingresses') {
      this.api.k8s('ingresses').subscribe({
        next: (items) => { this.ingresses.set(items); this.loading.set(false); },
        error: (err: unknown) => this.fail(err)
      });
    }
    if (view === 'deployments') {
      this.api.k8s('deployments').subscribe({
        next: (items) => { this.deployments.set(items); this.loading.set(false); },
        error: (err: unknown) => this.fail(err)
      });
    }
  }

  filteredPods(): PodView[] {
    return this.pods().filter((item) => this.matches(item.name, item.namespace, item.phase));
  }

  filteredServices(): K8sServiceRow[] {
    return this.services().filter((item) => this.matches(item.name, item.namespace, item.type));
  }

  filteredIngresses(): K8sIngressRow[] {
    return this.ingresses().filter((item) => this.matches(item.name, item.namespace, item.hosts.join(' ')));
  }

  filteredDeployments(): K8sDeploymentRow[] {
    return this.deployments().filter((item) => this.matches(item.name, item.namespace, item.images.join(' ')));
  }

  filteredEvents(): K8sEventRow[] {
    return this.events().filter((item) => this.matches(item.reason, item.involvedObject, item.message));
  }

  ready(item: K8sDeploymentRow): boolean {
    return (item.replicas ?? 0) > 0 && item.readyReplicas === item.replicas;
  }

  selectPod(item: PodView): void {
    this.pod.set(item);
    this.deployment.set(null);
    this.detailLog.set('');
    if (!this.events().length) {
      this.api.k8s('events').subscribe({ next: (items) => this.events.set(items), error: () => undefined });
    }
  }

  selectDeployment(item: K8sDeploymentRow): void {
    this.deployment.set(item);
    this.pod.set(null);
    this.replicas = item.replicas ?? 1;
    this.revisions.set([]);
    this.api.k8sRevisions(item.name).subscribe({
      next: (items) => this.revisions.set(items),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  logs(name: string): void {
    this.api.podLogs(name).subscribe({
      next: (text) => this.detailLog.set(text || 'Journal vide.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  related(name: string): K8sEventRow[] {
    return this.events().filter((item) => item.involvedObject.includes(name) || item.message.includes(name));
  }

  confirmScale(name: string): void {
    this.confirm('Changer l’échelle', `Demander ${this.replicas} replica(s) pour ${name} ?`, () => {
      this.api.scale(name, this.replicas).subscribe({
        next: () => this.load(),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  confirmRestart(name: string): void {
    this.confirm('Redémarrer', `Relancer les Pods de ${name} ?`, () => {
      this.api.restart(name).subscribe({
        next: () => this.load(),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  private confirm(title: string, message: string, action: () => void): void {
    this.dialog.open(ConfirmDialog, { data: { title, message } }).afterClosed().subscribe((ok) => {
      if (ok) {
        action();
      }
    });
  }

  private matches(...fields: string[]): boolean {
    const needle = this.query.trim().toLowerCase();
    return !needle || fields.join(' ').toLowerCase().includes(needle);
  }

  private fail(err: unknown): void {
    this.error.set(errorMessage(err));
    this.loading.set(false);
  }
}

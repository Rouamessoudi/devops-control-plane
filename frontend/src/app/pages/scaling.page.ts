import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { K8sDeploymentRow, ReleaseResult } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-scaling',
  imports: [ReactiveFormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <div class="page-head">
        <h1>Déploiements et autoscaling</h1>
        <button mat-stroked-button type="button" (click)="refresh()">Actualiser</button>
      </div>
      @if (metrics()) { <p class="muted">Metrics Server : {{ metrics()!.available ? 'disponible' : 'absent' }}. {{ metrics()!.message }}</p> }
      @if (message()) { <p>{{ message() }}</p> }
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <article class="panel">
        <h2>Blue/green</h2>
        <p class="muted">Le Service ne change de sélecteur qu'après des replicas prêts. La bascule réutilise la piste déjà déployée.</p>
        <form class="form-grid" [formGroup]="blueGreen" (ngSubmit)="submitBlueGreen()">
          <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Application</mat-label><input matInput formControlName="applicationId"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image candidate</mat-label><input matInput formControlName="candidateImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image active</mat-label><input matInput formControlName="activeImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Piste</mat-label><input matInput formControlName="candidateTrack"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Replicas</mat-label><input matInput type="number" formControlName="replicas"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Port</mat-label><input matInput type="number" formControlName="containerPort"></mat-form-field>
          <button mat-flat-button type="submit">Déployer la piste</button>
        </form>
        <form class="form-grid" [formGroup]="trackSwitch" (ngSubmit)="submitSwitch()">
          <mat-form-field appearance="outline"><mat-label>Nom à basculer</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Piste cible</mat-label><input matInput formControlName="track"></mat-form-field>
          <button mat-stroked-button type="submit">Basculer le Service</button>
        </form>
      </article>
      <article class="panel">
        <h2>Canary</h2>
        <p class="muted">La route n'accepte que le nom d'hôte nom.dcp.local. Une route sans nom d'hôte, comme une autre application, ne reçoit pas ce trafic. « Route acceptée » signifie que le Gateway a pris la route : ce n'est pas un comptage HTTP.</p>
        <form class="form-grid" [formGroup]="canary" (ngSubmit)="submitCanary()">
          <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Application</mat-label><input matInput formControlName="applicationId"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image stable</mat-label><input matInput formControlName="stableImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image canary</mat-label><input matInput formControlName="canaryImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Poids canary</mat-label><input matInput type="number" formControlName="canaryWeight"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Replicas</mat-label><input matInput type="number" formControlName="replicas"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Port</mat-label><input matInput type="number" formControlName="containerPort"></mat-form-field>
          <button mat-flat-button type="submit">Appliquer le poids</button>
        </form>
      </article>
      <article class="panel">
        <h2>Rollback Kubernetes</h2>
        <p class="muted">Ceci annule une révision du Deployment. Ce n'est pas le rollback d'image d'une fiche application.</p>
        <form class="form-grid" [formGroup]="rollback" (ngSubmit)="loadRevisions()">
          <mat-form-field appearance="outline"><mat-label>Deployment</mat-label><input matInput formControlName="name"></mat-form-field>
          <button mat-stroked-button type="submit">Historique</button>
        </form>
        @if (revisions().length) {
          <table class="data">
            <thead><tr><th>Révision</th><th>Image</th><th>État</th><th></th></tr></thead>
            <tbody>
              @for (item of revisions(); track item.revision) {
                <tr>
                  <td>{{ item.revision }}</td>
                  <td>{{ item.image }}</td>
                  <td>{{ item.current ? 'courante' : 'précédente' }}</td>
                  <td>
                    @if (!item.current) { <button mat-button type="button" (click)="rollbackTo(item.revision)">Revenir à cette révision</button> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </article>
      <article class="panel">
        <h2>Horizontal Pod Autoscaler</h2>
        <form class="form-grid" [formGroup]="autoscaler" (ngSubmit)="submitAutoscaler()">
          <mat-form-field appearance="outline"><mat-label>Deployment</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Application</mat-label><input matInput formControlName="applicationId"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Min</mat-label><input matInput type="number" formControlName="minReplicas"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Max</mat-label><input matInput type="number" formControlName="maxReplicas"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>CPU %</mat-label><input matInput type="number" formControlName="cpuUtilization"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Mémoire %</mat-label><input matInput type="number" formControlName="memoryUtilization"></mat-form-field>
          <button mat-flat-button type="submit">Créer le HPA</button>
        </form>
      </article>
      @if (result(); as view) {
        <article class="panel">
          <h2>Dernière réponse</h2>
          <dl class="facts">
            <div><dt>Stratégie</dt><dd>{{ view.strategy || '—' }}</dd></div>
            <div><dt>Nom</dt><dd>{{ view.name || '—' }}</dd></div>
            <div><dt>Image</dt><dd>{{ view.image || '—' }}</dd></div>
            <div><dt>Replicas</dt><dd>{{ view.replicas ?? '—' }}</dd></div>
            <div><dt>Poids canary</dt><dd>{{ view.canaryWeight ?? '—' }}</dd></div>
            <div><dt>Piste active</dt><dd>{{ view.activeTrack || '—' }}</dd></div>
            <div><dt>Route acceptée</dt><dd>{{ view.trafficApplied == null ? '—' : (view.trafficApplied ? 'oui' : 'non') }}</dd></div>
            <div><dt>État</dt><dd><span [class]="'badge ' + badge(view.status)">{{ view.status || '—' }}</span></dd></div>
          </dl>
          @if (view.message) { <p>{{ view.message }}</p> }
        </article>
      }
      <article class="panel table-wrap">
        <h2>État réel des Deployments</h2>
        <table class="data">
          <thead><tr><th>Nom</th><th>Namespace</th><th>Prêts</th><th>Image</th></tr></thead>
          <tbody>
            @for (item of live(); track item.name) {
              <tr>
                <td>{{ item.name }}</td>
                <td>{{ item.namespace }}</td>
                <td>{{ item.readyReplicas ?? 0 }}/{{ item.replicas ?? 0 }}</td>
                <td>{{ item.images[0] || '—' }}</td>
              </tr>
            } @empty { <tr><td colspan="4">Aucun Deployment renvoyé.</td></tr> }
          </tbody>
        </table>
      </article>
    </section>
  `
})
export class ScalingPage {
  private readonly api = inject(ApiService);
  private readonly forms = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);
  readonly metrics = signal<{ available: boolean; message: string } | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  readonly result = signal<ReleaseResult | null>(null);
  readonly live = signal<K8sDeploymentRow[]>([]);
  readonly badge = badgeClass;
  readonly revisions = signal<{ revision: number; image: string; current: boolean }[]>([]);
  readonly blueGreen = this.forms.nonNullable.group({
    name: ['', Validators.required],
    applicationId: ['', Validators.required],
    activeImage: ['', Validators.required],
    candidateImage: ['', Validators.required],
    candidateTrack: ['green', Validators.required],
    replicas: [1, Validators.required],
    containerPort: [0, [Validators.required, Validators.min(1)]]
  });
  readonly trackSwitch = this.forms.nonNullable.group({
    name: ['', Validators.required],
    track: ['green', Validators.required]
  });
  readonly canary = this.forms.nonNullable.group({
    name: ['', Validators.required],
    applicationId: ['', Validators.required],
    stableImage: ['', Validators.required],
    canaryImage: ['', Validators.required],
    replicas: [1, Validators.required],
    containerPort: [0, [Validators.required, Validators.min(1)]],
    canaryWeight: [10, Validators.required]
  });
  readonly rollback = this.forms.nonNullable.group({
    name: ['', Validators.required]
  });
  readonly autoscaler = this.forms.nonNullable.group({
    name: ['', Validators.required],
    applicationId: ['', Validators.required],
    minReplicas: [1, Validators.required],
    maxReplicas: [3, Validators.required],
    cpuUtilization: [70, Validators.required],
    memoryUtilization: [80, Validators.required]
  });

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.api.metricsServer().subscribe({
      next: (status) => this.metrics.set(status),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
    this.api.k8s('deployments').subscribe({
      next: (items) => this.live.set(items),
      error: () => this.live.set([])
    });
  }

  submitBlueGreen(): void {
    if (this.blueGreen.invalid) {
      this.error.set('Renseignez le nom, l’application, les deux images et un port supérieur à 0.');
      return;
    }
    const value = this.blueGreen.getRawValue();
    const track = value.candidateTrack === 'blue' ? 'blue' : 'green';
    this.confirm('Blue/green', `Déployer la piste ${track} de ${value.name || 'ce service'} ?`, () => {
      this.api.blueGreen({ ...value, candidateTrack: track }).subscribe({
        next: (body) => this.show(body),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  submitSwitch(): void {
    const value = this.trackSwitch.getRawValue();
    const track = value.track === 'blue' ? 'blue' : value.track === 'green' ? 'green' : '';
    if (!track) {
      this.error.set('La piste cible doit être blue ou green.');
      return;
    }
    this.confirm('Blue/green', `Basculer le Service ${value.name} vers la piste ${track} ?`, () => {
      this.api.switchTrack(value.name, track).subscribe({
        next: (body) => this.show(body),
        error: (err: unknown) => this.fail(err)
      });
    });
  }

  submitCanary(): void {
    if (this.canary.invalid) {
      this.error.set('Renseignez le nom, l’application, les deux images et un port supérieur à 0.');
      return;
    }
    const value = this.canary.getRawValue();
    this.confirm('Canary', `Appliquer le poids ${value.canaryWeight} sur ${value.name || 'ce service'} ?`, () => {
      this.api.canary(value).subscribe({
        next: (body) => this.show(body),
        error: (err: unknown) => this.fail(err)
      });
    });
  }

  loadRevisions(): void {
    const name = this.rollback.getRawValue().name;
    this.api.k8sRevisions(name).subscribe({
      next: (items) => {
        this.revisions.set(items);
        this.error.set('');
        this.message.set(items.length + ' révision(s) Kubernetes.');
      },
      error: (err: unknown) => this.fail(err)
    });
  }

  rollbackTo(revision: number): void {
    const name = this.rollback.getRawValue().name;
    this.confirm('Rollback Kubernetes', `Revenir à la révision ${revision} de ${name} ?`, () => {
      this.api.k8sRollback(name, revision).subscribe({
        next: (body) => {
          this.show(body);
          this.loadRevisions();
        },
        error: (err: unknown) => this.fail(err)
      });
    });
  }

  submitAutoscaler(): void {
    const value = this.autoscaler.getRawValue();
    this.confirm('Autoscaler', `Créer ou mettre à jour le HPA de ${value.name} ?`, () => {
      this.api.autoscaler(value.name, value).subscribe({
        next: (body) => this.show(body),
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

  private show(body: unknown): void {
    const view = body as ReleaseResult;
    this.result.set(view);
    if (view.trafficApplied === false) {
      this.message.set('');
      this.error.set(view.message || 'Le trafic n’a pas été confirmé.');
      return;
    }
    this.error.set('');
    this.message.set(view.message || 'Réponse du service Kubernetes.');
  }

  private fail(err: unknown): void {
    this.message.set('');
    this.error.set(errorMessage(err));
  }
}

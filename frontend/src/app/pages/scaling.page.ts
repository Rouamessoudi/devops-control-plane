import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/ui';

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
        <p class="muted">Le Service ne change de sélecteur qu'après des replicas prêts. Le retour se fait vers l'autre piste, qui reste déployée.</p>
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
      </article>
      <article class="panel">
        <h2>Canary</h2>
        <p class="muted">Les poids sont écrits dans un HTTPRoute Gateway API. Le trafic n'est marqué appliqué que si les Pods sont prêts et que le Gateway accepte la route.</p>
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
        @for (item of revisions(); track item.revision) {
          <p>
            Révision {{ item.revision }} · {{ item.image }}
            @if (item.current) { <span> · révision courante</span> }
            @else { <button mat-button type="button" (click)="rollbackTo(item.revision)">Revenir à cette révision</button> }
          </p>
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
      @if (result()) { <pre class="log">{{ result() }}</pre> }
    </section>
  `
})
export class ScalingPage {
  private readonly api = inject(ApiService);
  private readonly forms = inject(FormBuilder);
  readonly metrics = signal<{ available: boolean; message: string } | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  readonly result = signal('');
  readonly revisions = signal<{ revision: number; image: string; current: boolean }[]>([]);
  readonly blueGreen = this.forms.nonNullable.group({
    name: ['', Validators.required],
    applicationId: ['', Validators.required],
    activeImage: ['registry:5000/dcp/app:1', Validators.required],
    candidateImage: ['registry:5000/dcp/app:2', Validators.required],
    candidateTrack: ['green', Validators.required],
    replicas: [1, Validators.required],
    containerPort: [8080, Validators.required]
  });
  readonly canary = this.forms.nonNullable.group({
    name: ['', Validators.required],
    applicationId: ['', Validators.required],
    stableImage: ['registry:5000/dcp/web:stable', Validators.required],
    canaryImage: ['registry:5000/dcp/web:canary', Validators.required],
    replicas: [1, Validators.required],
    containerPort: [80, Validators.required],
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
  }

  submitBlueGreen(): void {
    const value = this.blueGreen.getRawValue();
    const track = value.candidateTrack === 'blue' ? 'blue' : 'green';
    this.api.blueGreen({ ...value, candidateTrack: track }).subscribe({
      next: (body) => this.show(body),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  submitCanary(): void {
    this.api.canary(this.canary.getRawValue()).subscribe({
      next: (body) => this.show(body),
      error: (err: unknown) => this.fail(err)
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
    this.api.k8sRollback(name, revision).subscribe({
      next: (body) => {
        this.show(body);
        this.loadRevisions();
      },
      error: (err: unknown) => this.fail(err)
    });
  }

  submitAutoscaler(): void {
    const value = this.autoscaler.getRawValue();
    this.api.autoscaler(value.name, value).subscribe({
      next: (body) => this.show(body),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  private show(body: unknown): void {
    const view = body as { trafficApplied?: boolean; message?: string };
    this.result.set(JSON.stringify(body, null, 2));
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

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
        <p class="muted">Les poids sont écrits dans un HTTPRoute Gateway API. Sans ce contrôleur, l'API refuse l'opération et le trafic ne change pas.</p>
        <form class="form-grid" [formGroup]="canary" (ngSubmit)="submitCanary()">
          <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Application</mat-label><input matInput formControlName="applicationId"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image stable</mat-label><input matInput formControlName="stableImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Image canary</mat-label><input matInput formControlName="canaryImage"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Poids canary</mat-label><input matInput type="number" formControlName="canaryWeight"></mat-form-field>
          <button mat-flat-button type="submit">Appliquer le poids</button>
        </form>
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
    stableImage: ['registry:5000/dcp/app:1', Validators.required],
    canaryImage: ['registry:5000/dcp/app:2', Validators.required],
    replicas: [1, Validators.required],
    containerPort: [8080, Validators.required],
    canaryWeight: [10, Validators.required]
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
      error: (err: unknown) => this.error.set(errorMessage(err))
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
    this.error.set('');
    this.message.set('Réponse du service Kubernetes.');
    this.result.set(JSON.stringify(body, null, 2));
  }
}

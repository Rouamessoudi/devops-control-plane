import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-application-form',
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatSelect, MatOption, MatButton],
  template: `
    <section class="page">
      <h1>{{ id ? 'Modifier' : 'Créer' }} une application</h1>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <form class="form-grid" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput formControlName="name"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Description</mat-label><input matInput formControlName="description"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Dépôt GitHub</mat-label><input matInput formControlName="gitRepositoryUrl"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Branche</mat-label><input matInput formControlName="branch"></mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Environnement</mat-label>
          <mat-select formControlName="environment">
            <mat-option value="DEV">DEV</mat-option>
            <mat-option value="STAGING">STAGING</mat-option>
            <mat-option value="PROD">PROD</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Dockerfile</mat-label><input matInput formControlName="dockerfilePath"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Contexte</mat-label><input matInput formControlName="contextPath"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Port</mat-label><input matInput type="number" formControlName="containerPort"></mat-form-field>
        <button mat-flat-button type="submit" [disabled]="form.invalid">Enregistrer</button>
      </form>
    </section>
  `
})
export class ApplicationFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly id = this.route.snapshot.paramMap.get('id');
  readonly error = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]{1,38}$/)]],
    description: [''],
    gitRepositoryUrl: ['', Validators.required],
    branch: ['main', Validators.required],
    environment: ['DEV', Validators.required],
    dockerfilePath: ['Dockerfile', Validators.required],
    contextPath: ['.', Validators.required],
    containerPort: [80, [Validators.required, Validators.min(1), Validators.max(65535)]]
  });

  constructor() {
    if (this.id) {
      this.api.application(this.id).subscribe({
        next: (app) => this.form.patchValue({
          name: app.name,
          description: app.description ?? '',
          gitRepositoryUrl: app.gitRepositoryUrl,
          branch: app.branch,
          environment: app.environment,
          dockerfilePath: app.dockerfilePath,
          contextPath: app.contextPath,
          containerPort: app.containerPort
        }),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    }
  }

  submit(): void {
    const body = this.form.getRawValue();
    const request = this.id ? this.api.updateApplication(this.id, body) : this.api.createApplication(body);
    request.subscribe({
      next: (app) => void this.router.navigate(['/applications', app.id]),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }
}

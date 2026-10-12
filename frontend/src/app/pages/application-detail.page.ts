import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { ApplicationView, DeploymentView, EnvVarView, HistoryView } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-application-detail',
  imports: [RouterLink, ReactiveFormsModule, MatButton, MatFormField, MatLabel, MatInput, MatCheckbox],
  template: `
    <section class="page">
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (app(); as current) {
        <div class="page-head">
          <div>
            <h1>{{ current.name }}</h1>
            <p class="muted">{{ current.gitRepositoryUrl }} · {{ current.branch }}</p>
          </div>
          <div class="row-actions">
            <span [class]="'badge ' + badge(current.status)">{{ current.status }}</span>
            <a mat-button [routerLink]="['/applications', current.id, 'edit']">Modifier</a>
            <button mat-flat-button type="button" (click)="deploy()">Déployer</button>
            <button mat-stroked-button type="button" (click)="pipeline()">Pipeline</button>
          </div>
        </div>
        <article class="panel">
          <h2>Variables d'environnement</h2>
          <p class="muted">Les secrets restent masqués dans cette réponse API.</p>
          <ul>
            @for (item of env(); track item.id) { <li>{{ item.key }} = {{ item.value }} @if (item.secret) { <span class="badge">secret</span> }
              <button mat-button type="button" (click)="removeEnv(item)">Retirer</button></li>
            }
          </ul>
          <form class="row-actions" [formGroup]="envForm" (ngSubmit)="addEnv()">
            <mat-form-field appearance="outline"><mat-label>Clé</mat-label><input matInput formControlName="key"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Valeur</mat-label><input matInput formControlName="value"></mat-form-field>
            <mat-checkbox formControlName="secret">Secret</mat-checkbox>
            <button mat-button type="submit">Ajouter</button>
          </form>
        </article>
        <article class="panel table-wrap">
          <h2>Historique</h2>
          <table class="data">
            <thead><tr><th>Action</th><th>Résumé</th><th>Date</th></tr></thead>
            <tbody>
              @for (item of history(); track item.id) {
                <tr><td>{{ item.action }}</td><td>{{ item.summary || '—' }}</td><td>{{ item.createdAt }}</td></tr>
              } @empty { <tr><td colspan="3">Aucun événement.</td></tr> }
            </tbody>
          </table>
        </article>
        <article class="panel table-wrap">
          <h2>Déploiements</h2>
          <table class="data">
            <thead><tr><th>Statut</th><th>Image</th><th>Révision</th><th></th></tr></thead>
            <tbody>
              @for (item of deployments(); track item.id) {
                <tr>
                  <td><span [class]="'badge ' + badge(item.status)">{{ item.status }}</span></td>
                  <td>{{ item.fullImage }}</td>
                  <td>{{ item.k8sRevision ?? '—' }}</td>
                  <td><button mat-button type="button" (click)="rollback(item)">Rollback</button></td>
                </tr>
              }
            </tbody>
          </table>
          @if (message()) { <p>{{ message() }}</p> }
        </article>
      }
    </section>
  `
})
export class ApplicationDetailPage {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly app = signal<ApplicationView | null>(null);
  readonly env = signal<EnvVarView[]>([]);
  readonly deployments = signal<DeploymentView[]>([]);
  readonly historyText = signal('');
  readonly history = signal<HistoryView[]>([]);
  readonly error = signal('');
  readonly message = signal('');
  readonly badge = badgeClass;
  readonly envForm = inject(FormBuilder).nonNullable.group({
    key: ['', Validators.required],
    value: ['', Validators.required],
    secret: [false]
  });

  constructor() { this.reload(); }

  reload(): void {
    this.api.application(this.id).subscribe({ next: (app) => this.app.set(app), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.env(this.id).subscribe({ next: (items) => this.env.set(items), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.history(this.id).subscribe({ next: (items) => this.history.set(items), error: () => this.history.set([]) });
    this.api.deployments(this.id).subscribe({ next: (page) => this.deployments.set(page.items ?? []), error: () => this.deployments.set([]) });
  }

  addEnv(): void {
    if (this.envForm.invalid) { return; }
    this.api.addEnv(this.id, this.envForm.getRawValue()).subscribe({
      next: () => { this.envForm.reset({ key: '', value: '', secret: false }); this.reload(); },
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  removeEnv(item: EnvVarView): void {
    if (!item.id) { return; }
    this.confirm('Retirer la variable', item.key, () => {
      this.api.deleteEnv(this.id, item.id!).subscribe({ next: () => this.reload(), error: (err: unknown) => this.error.set(errorMessage(err)) });
    });
  }

  deploy(): void {
    this.confirm('Déployer', 'Lancer un déploiement Kubernetes pour cette application ?', () => {
      this.api.deploy({ applicationId: this.id, replicas: 1 }).subscribe({
        next: (deployment) => { this.message.set('Déploiement ' + deployment.id + ' : ' + deployment.status); this.reload(); },
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  pipeline(): void {
    this.confirm('Pipeline', 'Lancer le pipeline de cette application ?', () => {
      this.api.startPipeline(this.id).subscribe({
        next: (pipeline) => void this.router.navigate(['/pipelines', pipeline.id]),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  rollback(item: DeploymentView): void {
    this.confirm('Rollback applicatif', 'Revenir à ce déploiement ?', () => {
      this.api.rollback(item.id).subscribe({
        next: (deployment) => this.message.set('Rollback ' + deployment.id),
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

  private get id(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }
}

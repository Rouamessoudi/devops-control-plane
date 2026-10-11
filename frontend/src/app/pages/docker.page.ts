import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { ApplicationView, BuildView } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';

@Component({
  selector: 'app-docker',
  imports: [FormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <h1>Docker</h1>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <article class="panel">
        <h2>Construire une image</h2>
        <div class="row-actions">
          <mat-form-field appearance="outline">
            <mat-label>Application</mat-label>
            <input matInput [(ngModel)]="applicationId" placeholder="identifiant">
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Tag</mat-label><input matInput [(ngModel)]="tag"></mat-form-field>
          <button mat-flat-button type="button" (click)="build()">Construire</button>
        </div>
        <ul>
          @for (app of apps(); track app.id) { <li><button mat-button type="button" (click)="applicationId = app.id">{{ app.name }}</button></li> }
        </ul>
      </article>
      <article class="panel table-wrap">
        <h2>Builds</h2>
        <table class="data">
          <thead><tr><th>Statut</th><th>Image</th><th>Début</th><th></th></tr></thead>
          <tbody>
            @for (item of builds(); track item.id) {
              <tr>
                <td><span [class]="'badge ' + badge(item.status)">{{ item.status }}</span></td>
                <td>{{ item.fullImage }}</td>
                <td>{{ item.startedAt }}</td>
                <td><button mat-button type="button" (click)="open(item.id)">Logs</button></td>
              </tr>
            }
          </tbody>
        </table>
        @if (log()) { <pre class="log">{{ log() }}</pre> }
      </article>
      <article class="panel">
        <h2>Images et conteneurs gérés</h2>
        <pre class="log">{{ inventory() }}</pre>
      </article>
    </section>
  `
})
export class DockerPage {
  private readonly api = inject(ApiService);
  readonly apps = signal<ApplicationView[]>([]);
  readonly builds = signal<BuildView[]>([]);
  readonly error = signal('');
  readonly log = signal('');
  readonly inventory = signal('');
  applicationId = '';
  tag = '';
  readonly badge = badgeClass;

  constructor() {
    this.api.applications().subscribe({ next: (page) => this.apps.set(page.items), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.reload();
  }

  reload(): void {
    this.api.builds().subscribe({ next: (items) => this.builds.set(items), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.images().subscribe({
      next: (images) => this.api.containers().subscribe({
        next: (containers) => this.inventory.set(JSON.stringify({ images, containers }, null, 2)),
        error: (err: unknown) => this.inventory.set(JSON.stringify({ images, containersError: errorMessage(err) }, null, 2))
      }),
      error: (err: unknown) => this.inventory.set(errorMessage(err))
    });
  }

  build(): void {
    this.api.build(this.applicationId, this.tag || undefined).subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  open(id: string): void {
    this.api.buildDetails(id).subscribe({
      next: (build) => this.log.set(build.logText || build.errorMessage || 'Pas encore de journal.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }
}

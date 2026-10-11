import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { ApplicationView } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-applications',
  imports: [RouterLink, FormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <div class="page-head">
        <h1>Applications</h1>
        <a mat-flat-button routerLink="/applications/new">Créer</a>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <mat-form-field appearance="outline">
        <mat-label>Filtrer</mat-label>
        <input matInput [(ngModel)]="filter" placeholder="nom ou dépôt">
      </mat-form-field>
      <article class="panel table-wrap">
        <table class="data">
          <thead><tr><th>Nom</th><th>Dépôt</th><th>Branche</th><th>Env</th><th>Statut</th><th></th></tr></thead>
          <tbody>
            @for (app of visible(); track app.id) {
              <tr>
                <td><a [routerLink]="['/applications', app.id]">{{ app.name }}</a></td>
                <td>{{ app.gitRepositoryUrl }}</td>
                <td>{{ app.branch }}</td>
                <td>{{ app.environment }}</td>
                <td><span [class]="'badge ' + badge(app.status)">{{ app.status }}</span></td>
                <td><button mat-button type="button" (click)="remove(app)">Supprimer</button></td>
              </tr>
            } @empty { <tr><td colspan="6">Aucune application.</td></tr> }
          </tbody>
        </table>
      </article>
    </section>
  `
})
export class ApplicationsPage {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  readonly apps = signal<ApplicationView[]>([]);
  readonly error = signal('');
  filter = '';
  readonly badge = badgeClass;

  constructor() { this.load(); }

  visible(): ApplicationView[] {
    const query = this.filter.trim().toLowerCase();
    return this.apps().filter((app) => !query || app.name.includes(query) || app.gitRepositoryUrl.toLowerCase().includes(query));
  }

  load(): void {
    this.api.applications().subscribe({
      next: (page) => this.apps.set(page.items),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  remove(app: ApplicationView): void {
    this.dialog.open(ConfirmDialog, { data: { title: 'Supprimer', message: `Supprimer ${app.name} ?` } })
      .afterClosed().subscribe((ok: boolean) => {
        if (!ok) { return; }
        this.api.deleteApplication(app.id).subscribe({ next: () => this.load(), error: (err: unknown) => this.error.set(errorMessage(err)) });
      });
  }
}

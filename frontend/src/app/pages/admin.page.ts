import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ApiService } from '../core/api.service';
import { UserProfile } from '../core/models';
import { errorMessage } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-admin',
  imports: [MatButton],
  template: `
    <section class="page">
      <h1>Administration</h1>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <article class="panel">
        <h2>Infrastructure</h2>
        <pre class="log">{{ infra() }}</pre>
      </article>
      <article class="panel table-wrap">
        <h2>Utilisateurs</h2>
        <table class="data">
          <thead><tr><th>Email</th><th>Nom</th><th>Rôles</th><th></th></tr></thead>
          <tbody>
            @for (user of users(); track user.id) {
              <tr>
                <td>{{ user.email }}</td>
                <td>{{ user.fullName }}</td>
                <td>{{ user.roles.join(', ') }}</td>
                <td class="row-actions">
                  <button mat-button type="button" (click)="grant(user, ['ADMIN', 'DEVELOPER'])">Admin</button>
                  <button mat-button type="button" (click)="grant(user, ['DEVELOPER'])">Developer</button>
                  <button mat-button type="button" (click)="remove(user)">Supprimer</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </article>
      <article class="panel">
        <h2>Audit authentification</h2>
        <pre class="log">{{ audit() }}</pre>
      </article>
    </section>
  `
})
export class AdminPage {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  readonly users = signal<UserProfile[]>([]);
  readonly infra = signal('');
  readonly audit = signal('');
  readonly error = signal('');

  constructor() { this.load(); }

  load(): void {
    this.api.users().subscribe({ next: (users) => this.users.set(users), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.k8sStatus().subscribe({
      next: (status) => this.api.monitoring().subscribe({
        next: (metrics) => this.infra.set(JSON.stringify({ status, metrics }, null, 2)),
        error: (err: unknown) => this.infra.set(JSON.stringify({ status, metricsError: errorMessage(err) }, null, 2))
      }),
      error: (err: unknown) => this.infra.set(errorMessage(err))
    });
    this.api.audit('/api/auth/audit-events').subscribe({
      next: (page) => this.audit.set(JSON.stringify(page.items, null, 2)),
      error: (err: unknown) => this.audit.set(errorMessage(err))
    });
  }

  grant(user: UserProfile, roles: string[]): void {
    this.api.updateRoles(user.id, roles).subscribe({ next: () => this.load(), error: (err: unknown) => this.error.set(errorMessage(err)) });
  }

  remove(user: UserProfile): void {
    this.dialog.open(ConfirmDialog, { data: { title: 'Supprimer l’utilisateur', message: user.email } })
      .afterClosed().subscribe((ok: boolean) => {
        if (!ok) { return; }
        this.api.deleteUser(user.id).subscribe({ next: () => this.load(), error: (err: unknown) => this.error.set(errorMessage(err)) });
      });
  }
}

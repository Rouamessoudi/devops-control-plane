import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { ApplicationView, BuildView, DockerContainerRow, DockerImageRow } from '../core/models';
import { badgeClass, errorMessage, formatAge, formatBytes } from '../core/ui';
import { ConfirmDialog } from '../shared/confirm.dialog';

@Component({
  selector: 'app-docker',
  imports: [FormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Docker</h1>
          <p class="muted">Seuls les conteneurs portant le label dcp.managed=true sont listés. Les autres conteneurs de la machine ne sont pas visibles. Cette API ne fournit ni port ni CPU/RAM.</p>
        </div>
        <button mat-stroked-button type="button" (click)="reload()">Actualiser</button>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (warning()) { <p class="error-banner">{{ warning() }}</p> }
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
        <div class="row-actions">
          @for (app of apps(); track app.id) { <button mat-button type="button" (click)="applicationId = app.id">{{ app.name }}</button> }
        </div>
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
            } @empty { <tr><td colspan="4">Aucun build enregistré.</td></tr> }
          </tbody>
        </table>
        @if (log()) { <pre class="log">{{ log() }}</pre> }
      </article>
      <div class="toolbar">
        <mat-form-field appearance="outline">
          <mat-label>Rechercher</mat-label>
          <input matInput [(ngModel)]="query" placeholder="nom, image ou statut">
        </mat-form-field>
      </div>
      <article class="panel table-wrap">
        <h2>Conteneurs</h2>
        <table class="data">
          <thead><tr><th>Nom</th><th>Image</th><th>État</th><th>Statut</th><th>Ports</th><th>CPU / RAM</th><th>Âge</th><th></th></tr></thead>
          <tbody>
            @for (item of filteredContainers(); track item.id) {
              <tr>
                <td>{{ item.names[0] || item.id }}</td>
                <td>{{ item.image }}</td>
                <td><span [class]="'badge ' + badge(item.state)">{{ item.state }}</span></td>
                <td>{{ item.status }}</td>
                <td class="muted">Non exposé</td>
                <td class="muted">Non exposé</td>
                <td>{{ age(item.createdAt) }}</td>
                <td><button mat-button type="button" (click)="selectContainer(item)">Détails</button></td>
              </tr>
            } @empty { <tr><td colspan="8">{{ query.trim() ? 'Aucun conteneur ne correspond.' : 'Aucun conteneur dcp.managed. Le démon peut répondre sans qu’un conteneur de la plateforme soit en cours.' }}</td></tr> }
          </tbody>
        </table>
      </article>
      @if (selected(); as item) {
        <article class="panel">
          <h2>{{ item.names[0] || item.id }}</h2>
          <dl class="facts">
            <div><dt>Identifiant</dt><dd>{{ item.id }}</dd></div>
            <div><dt>Image</dt><dd>{{ item.image }}</dd></div>
            <div><dt>État</dt><dd>{{ item.state }}</dd></div>
            <div><dt>Statut</dt><dd>{{ item.status }}</dd></div>
            <div><dt>Créé</dt><dd>{{ item.createdAt || '—' }}</dd></div>
          </dl>
          <button mat-stroked-button type="button" (click)="containerLog(item.id)">Logs</button>
          @if (containerText()) { <pre class="log">{{ containerText() }}</pre> }
        </article>
      }
      <article class="panel table-wrap">
        <h2>Images</h2>
        <table class="data">
          <thead><tr><th>Référentiel</th><th>Tag</th><th>Taille</th><th>Source</th><th>Créée</th></tr></thead>
          <tbody>
            @for (item of filteredImages(); track item.id) {
              <tr>
                <td>{{ item.repository }}</td>
                <td>{{ item.tag }}</td>
                <td>{{ bytes(item.sizeBytes) }}</td>
                <td>{{ item.source || '—' }}</td>
                <td>{{ item.createdAt || '—' }}</td>
              </tr>
            } @empty { <tr><td colspan="5">Aucune image ne correspond.</td></tr> }
          </tbody>
        </table>
      </article>
    </section>
  `
})
export class DockerPage {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  readonly apps = signal<ApplicationView[]>([]);
  readonly builds = signal<BuildView[]>([]);
  readonly images = signal<DockerImageRow[]>([]);
  readonly containers = signal<DockerContainerRow[]>([]);
  readonly selected = signal<DockerContainerRow | null>(null);
  readonly error = signal('');
  readonly warning = signal('');
  readonly log = signal('');
  readonly containerText = signal('');
  applicationId = '';
  tag = '';
  query = '';
  readonly badge = badgeClass;
  readonly bytes = formatBytes;
  readonly age = formatAge;

  constructor() {
    this.api.applications().subscribe({ next: (page) => this.apps.set(page.items), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.reload();
  }

  filteredContainers(): DockerContainerRow[] {
    return this.containers().filter((item) => this.matches([item.id, item.image, item.state, item.status, ...item.names]));
  }

  filteredImages(): DockerImageRow[] {
    return this.images().filter((item) => this.matches([item.repository, item.tag, item.source ?? '', item.dockerImageId ?? '']));
  }

  reload(): void {
    this.api.builds().subscribe({ next: (items) => this.builds.set(items), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.images().subscribe({
      next: (listing) => {
        this.images.set(listing.images ?? []);
        this.warning.set(listing.warning ?? '');
      },
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
    this.api.containers().subscribe({
      next: (items) => this.containers.set(items),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  build(): void {
    if (!this.applicationId) {
      this.error.set('Choisissez une application.');
      return;
    }
    this.dialog.open(ConfirmDialog, {
      data: { title: 'Lancer la construction', message: 'Cette action construit et pousse une image. Elle ne supprime rien.' }
    }).afterClosed().subscribe((ok) => {
      if (!ok) {
        return;
      }
      this.api.build(this.applicationId, this.tag || undefined).subscribe({
        next: () => this.reload(),
        error: (err: unknown) => this.error.set(errorMessage(err))
      });
    });
  }

  open(id: string): void {
    this.api.buildDetails(id).subscribe({
      next: (build) => this.log.set(build.logText || build.errorMessage || 'Pas encore de journal.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  selectContainer(item: DockerContainerRow): void {
    this.selected.set(item);
    this.containerText.set('');
  }

  containerLog(id: string): void {
    this.api.containerLogs(id).subscribe({
      next: (text) => this.containerText.set(text || 'Journal vide.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  private matches(fields: string[]): boolean {
    const needle = this.query.trim().toLowerCase();
    return !needle || fields.join(' ').toLowerCase().includes(needle);
  }
}

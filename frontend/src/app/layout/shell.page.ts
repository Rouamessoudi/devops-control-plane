import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { AuthService } from '../core/auth.service';
import { TokenStore } from '../core/token-store';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavContainer, MatSidenav, MatSidenavContent, MatIcon, MatButton, MatIconButton, MatMenu, MatMenuItem, MatMenuTrigger],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav mode="side" opened>
        <div class="brand"><mat-icon>hub</mat-icon> Control Plane</div>
        <a class="nav-link" routerLink="/dashboard" routerLinkActive="active"><mat-icon>dashboard</mat-icon> Tableau de bord</a>
        <a class="nav-link" routerLink="/applications" routerLinkActive="active"><mat-icon>apps</mat-icon> Applications</a>
        <a class="nav-link" routerLink="/docker" routerLinkActive="active"><mat-icon>view_in_ar</mat-icon> Docker</a>
        <a class="nav-link" routerLink="/kubernetes" routerLinkActive="active"><mat-icon>account_tree</mat-icon> Kubernetes</a>
        <a class="nav-link" routerLink="/kubernetes/pods" routerLinkActive="active"><mat-icon>crop_din</mat-icon> Pods</a>
        <a class="nav-link" routerLink="/kubernetes/services" routerLinkActive="active"><mat-icon>device_hub</mat-icon> Services</a>
        <a class="nav-link" routerLink="/kubernetes/ingresses" routerLinkActive="active"><mat-icon>alt_route</mat-icon> Ingress</a>
        <a class="nav-link" routerLink="/kubernetes/deployments" routerLinkActive="active"><mat-icon>deployed_code</mat-icon> Deployments</a>
        <a class="nav-link" routerLink="/kubernetes/scaling" routerLinkActive="active"><mat-icon>swap_horiz</mat-icon> Releases</a>
        <a class="nav-link" routerLink="/monitoring" routerLinkActive="active"><mat-icon>monitoring</mat-icon> Monitoring</a>
        <a class="nav-link" routerLink="/logs" routerLinkActive="active"><mat-icon>article</mat-icon> Logs</a>
        @if (tokens.hasRole('ADMIN')) {
          <a class="nav-link" routerLink="/admin" routerLinkActive="active"><mat-icon>admin_panel_settings</mat-icon> Administration</a>
        }
        <a class="nav-link" routerLink="/settings" routerLinkActive="active"><mat-icon>settings</mat-icon> Paramètres</a>
      </mat-sidenav>
      <mat-sidenav-content class="content">
        <header class="topbar">
          <span class="muted">GitHub → CI/CD → Docker → Kubernetes</span>
          <div>
            <button mat-icon-button type="button" (click)="toggleTheme()" aria-label="Changer le thème"><mat-icon>dark_mode</mat-icon></button>
            <button mat-button [matMenuTriggerFor]="menu">{{ tokens.user()?.fullName }}</button>
            <mat-menu #menu="matMenu">
              <button mat-menu-item type="button" (click)="logout()">Déconnexion</button>
            </mat-menu>
          </div>
        </header>
        <main class="main">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `
})
export class ShellPage {
  readonly tokens = inject(TokenStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  toggleTheme(): void {
    document.documentElement.classList.toggle('dark');
  }

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}

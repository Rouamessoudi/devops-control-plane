import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/auth.guard';
import { ShellPage } from './layout/shell.page';
import { AdminPage } from './pages/admin.page';
import { ApplicationDetailPage } from './pages/application-detail.page';
import { ApplicationFormPage } from './pages/application-form.page';
import { ApplicationsPage } from './pages/applications.page';
import { DashboardPage } from './pages/dashboard.page';
import { DockerPage } from './pages/docker.page';
import { KubernetesPage } from './pages/kubernetes.page';
import { LoginPage } from './pages/login.page';
import { LogsPage } from './pages/logs.page';
import { MonitoringPage } from './pages/monitoring.page';
import { PipelinePage } from './pages/pipeline.page';
import { RegisterPage } from './pages/register.page';
import { ScalingPage } from './pages/scaling.page';
import { SettingsPage } from './pages/settings.page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [guestGuard] },
  { path: 'register', component: RegisterPage, canActivate: [guestGuard] },
  {
    path: '',
    component: ShellPage,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardPage },
      { path: 'applications', component: ApplicationsPage },
      { path: 'applications/new', component: ApplicationFormPage },
      { path: 'applications/:id', component: ApplicationDetailPage },
      { path: 'applications/:id/edit', component: ApplicationFormPage },
      { path: 'docker', component: DockerPage },
      { path: 'kubernetes', component: KubernetesPage, data: { view: 'overview' } },
      { path: 'kubernetes/pods', component: KubernetesPage, data: { view: 'pods' } },
      { path: 'kubernetes/services', component: KubernetesPage, data: { view: 'services' } },
      { path: 'kubernetes/ingresses', component: KubernetesPage, data: { view: 'ingresses' } },
      { path: 'kubernetes/deployments', component: KubernetesPage, data: { view: 'deployments' } },
      { path: 'kubernetes/scaling', component: ScalingPage },
      { path: 'pipelines/:id', component: PipelinePage },
      { path: 'monitoring', component: MonitoringPage },
      { path: 'logs', component: LogsPage },
      { path: 'admin', component: AdminPage, canActivate: [adminGuard] },
      { path: 'settings', component: SettingsPage }
    ]
  }
];

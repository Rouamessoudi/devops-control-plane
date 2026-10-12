export function grafanaEmbedUrl(rawUrl?: string | null, dashboardPath = ''): string | null {
  if (!rawUrl) {
    return null;
  }
  let base: URL;
  try {
    base = new URL(rawUrl);
  } catch {
    return null;
  }
  const localHttp = base.protocol === 'http:'
    && (base.hostname === '127.0.0.1' || base.hostname === 'localhost')
    && base.port === '13001';
  if (!localHttp || !dashboardPath.startsWith('/') || dashboardPath.startsWith('//') || dashboardPath.includes('\\') || dashboardPath.includes('://')) {
    return null;
  }
  return `${base.origin}${dashboardPath}?orgId=1&kiosk`;
}

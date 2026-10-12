import { grafanaEmbedUrl } from './grafana-url';

describe('grafanaEmbedUrl', () => {
  it('accepts only the local Grafana origin and a relative dashboard path', () => {
    expect(grafanaEmbedUrl('http://127.0.0.1:13001', '/d/dcp-overview')).toBe('http://127.0.0.1:13001/d/dcp-overview?orgId=1&kiosk');
    expect(grafanaEmbedUrl('http://localhost:13001/', '/d/dcp-overview')).toBe('http://localhost:13001/d/dcp-overview?orgId=1&kiosk');
  });

  it('rejects foreign hosts, other schemes and absolute paths', () => {
    expect(grafanaEmbedUrl('https://127.0.0.1:13001', '/d/dcp')).toBeNull();
    expect(grafanaEmbedUrl('http://evil.example:13001', '/d/dcp')).toBeNull();
    expect(grafanaEmbedUrl('http://127.0.0.1:13001', 'https://evil.example/d')).toBeNull();
    expect(grafanaEmbedUrl('http://127.0.0.1:13001', '//evil.example/d')).toBeNull();
    expect(grafanaEmbedUrl('not a url', '/d/dcp')).toBeNull();
    expect(grafanaEmbedUrl(null, '/d/dcp')).toBeNull();
  });
});

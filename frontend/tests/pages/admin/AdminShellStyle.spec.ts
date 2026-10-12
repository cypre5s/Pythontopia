import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

function scopedStyles(componentPath: string): string {
  const source = readFileSync(new URL(componentPath, import.meta.url), 'utf8');
  return [...source.matchAll(/<style scoped[^>]*>([\s\S]*?)<\/style>/g)]
    .map((match) => match[1])
    .join('\n');
}

const layoutStyles = scopedStyles('../../../src/pages/admin/AdminLayout.vue');
const panelStyles = scopedStyles('../../../src/pages/admin/AdminPanel.vue');
const operationsStyles = scopedStyles('../../../src/pages/admin/AdminOperationsPage.vue');
const reviewStyles = scopedStyles('../../../src/pages/admin/AdminProblemReviewsPage.vue');

describe('admin visual shell contract', () => {
  it('uses the scoped blue-gray shell and Alethicode active navigation treatment', () => {
    expect(layoutStyles).toMatch(/--admin-bg:\s*linear-gradient\(180deg, #f8fbff 0%, #f3f7fc 100%\);/);
    expect(layoutStyles).toContain('--admin-panel-radius: 16px;');
    expect(layoutStyles).toContain('--admin-panel-shadow: 0 6px 18px rgb(15 23 42 / 8%);');
    expect(layoutStyles).toContain('--admin-table-header: #eef4fb;');
    expect(layoutStyles).toContain('--admin-table-hover: #f8fbff;');
    expect(layoutStyles).toMatch(/\.admin-nav__link\.is-active\s*\{[^}]*background:\s*linear-gradient\(90deg, #2563eb 0%, #3b82f6 100%\);/);
    expect(layoutStyles).toMatch(/\.admin-nav__link\.is-active\s*\{[^}]*color:\s*#fff;/);
    expect(layoutStyles).toContain('@media (max-width: 767px)');
    expect(layoutStyles).toContain('.admin-shell--nav-open .admin-sidebar');
    expect(layoutStyles).toContain('.admin-shell__navigation-toggle');
    expect(layoutStyles).not.toContain('.admin-topbar');
  });

  it('keeps panels and the review table on the shared surface and spacing scale', () => {
    expect(panelStyles).toMatch(/border-radius:\s*var\(--admin-panel-radius\);/);
    expect(panelStyles).toMatch(/box-shadow:\s*var\(--admin-panel-shadow\);/);
    expect(panelStyles).toMatch(/padding:\s*20px 22px 16px;/);
    expect(panelStyles).toMatch(/padding:\s*18px 22px 22px;/);

    expect(reviewStyles).toMatch(/\.review-table th\s*\{[^}]*background:\s*var\(--admin-table-header\);/);
    expect(reviewStyles).toMatch(/\.review-table td\s*\{[^}]*padding:\s*10px 16px;/);
    expect(reviewStyles).toContain('min-width: 1100px;');
    expect(reviewStyles).toMatch(/\.review-table td:nth-child\(1\),[\s\S]*?white-space:\s*nowrap;/);
    expect(reviewStyles).toContain('background: var(--admin-table-hover);');
    expect(operationsStyles).toMatch(/\.status-pill--insufficient-evidence\s*\{/);
    expect(operationsStyles).toMatch(/\.admin-page-heading\s*\{/);
  });
});

import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

function componentSource(componentPath: string): string {
  return readFileSync(new URL(componentPath, import.meta.url), 'utf8');
}

function scopedStyles(componentPath: string): string {
  const source = componentSource(componentPath);
  return [...source.matchAll(/<style scoped[^>]*>([\s\S]*?)<\/style>/g)]
    .map((match) => match[1])
    .join('\n');
}

const layoutStyles = scopedStyles('../../../src/pages/admin/AdminLayout.vue');
const panelStyles = scopedStyles('../../../src/pages/admin/AdminPanel.vue');
const operationsStyles = scopedStyles('../../../src/pages/admin/AdminOperationsPage.vue');
const reviewStyles = scopedStyles('../../../src/pages/admin/AdminProblemReviewsPage.vue');
const tableStyles = componentSource('../../../src/pages/admin/AdminTable.vue');
const reviewSource = componentSource('../../../src/pages/admin/AdminProblemReviewsPage.vue');
const usersSource = componentSource('../../../src/pages/admin/AdminUsersPage.vue');

describe('admin visual shell contract', () => {
  it('uses the scoped blue-gray shell and Alethicode active navigation treatment', () => {
    expect(layoutStyles).toMatch(/--admin-bg:\s*linear-gradient\(180deg, #f8fbff 0%, #f3f7fc 100%\);/);
    expect(layoutStyles).toContain('--admin-panel-radius: 16px;');
    expect(layoutStyles).toContain('--admin-panel-shadow: 0 6px 18px rgb(15 23 42 / 8%);');
    expect(layoutStyles).toContain('--admin-control-height: 36px;');
    expect(layoutStyles).toContain('--admin-control-radius: 8px;');
    expect(layoutStyles).toContain('--admin-table-header-height: 44px;');
    expect(layoutStyles).toContain('--admin-table-row-height: 52px;');
    expect(layoutStyles).toContain('--admin-dialog-radius: 16px;');
    expect(layoutStyles).toContain('.admin-shell :deep(.admin-page-heading h1)');
    expect(layoutStyles).toContain('--admin-table-header: #eef4fb;');
    expect(layoutStyles).toContain('--admin-table-hover: #f8fbff;');
    expect(layoutStyles).toMatch(/\.admin-nav__link\.is-active\s*\{[^}]*background:\s*linear-gradient\(90deg, #2563eb 0%, #3b82f6 100%\);/);
    expect(layoutStyles).toMatch(/\.admin-nav__link\.is-active\s*\{[^}]*color:\s*#fff;/);
    expect(layoutStyles).toContain('@media (max-width: 767px)');
    expect(layoutStyles).toContain('.admin-shell--nav-open .admin-sidebar');
    expect(layoutStyles).toContain('.admin-shell__navigation-toggle');
    expect(layoutStyles).not.toContain('.admin-topbar');
    expect(layoutStyles).toContain('.admin-shell :deep(.admin-dialog)');
    expect(componentSource('../../../src/pages/admin/AdminLayout.vue')).toContain('<svg');
    expect(componentSource('../../../src/pages/admin/AdminLayout.vue')).not.toContain('>01</span>');
  });

  it('matches Alethicode account and narrow navigation sizing', () => {
    expect(layoutStyles).toMatch(/\.admin-sidebar__logout\s*\{[^}]*width:\s*100%;[^}]*height:\s*42px;[^}]*background:\s*rgba\(239,\s*68,\s*68,\s*0\.1\);[^}]*color:\s*#dc2626;/);
    expect(layoutStyles).toContain('width: min(320px, 90vw);');
  });

  it('keeps panels and the review table on the shared surface and spacing scale', () => {
    expect(panelStyles).toMatch(/border-radius:\s*var\(--admin-panel-radius\);/);
    expect(panelStyles).toMatch(/box-shadow:\s*var\(--admin-panel-shadow\);/);
    expect(panelStyles).toMatch(/padding:\s*20px 22px 16px;/);
    expect(panelStyles).toMatch(/padding:\s*18px 22px 22px;/);

    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\s*\{[^}]*background:\s*var\(--admin-table-header, #eef4fb\);/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table td\s*\{[^}]*padding:\s*10px 16px;/);
    expect(reviewSource).toContain(':min-width="1100"');
    expect(reviewStyles).toMatch(/\.review-table td:nth-child\(1\),[\s\S]*?white-space:\s*nowrap;/);
    expect(reviewStyles).toContain('background: var(--admin-table-hover);');
    expect(operationsStyles).toMatch(/\.status-pill--insufficient-evidence\s*\{/);
    expect(operationsStyles).not.toContain('.admin-page-heading h1');
  });

  it('keeps the user table on the same row and cell spacing as the review table', () => {
    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\s*\{[^}]*height:\s*var\(--admin-table-header-height, 44px\);[^}]*padding:\s*0 16px;/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table td\s*\{[^}]*height:\s*var\(--admin-table-row-height, 52px\);[^}]*padding:\s*10px 16px;/);
    expect(usersSource).toContain(':min-width="820"');
  });
});

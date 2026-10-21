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
const layoutSource = componentSource('../../../src/pages/admin/AdminLayout.vue');
const panelStyles = scopedStyles('../../../src/pages/admin/AdminPanel.vue');
const panelSource = componentSource('../../../src/pages/admin/AdminPanel.vue');
const operationsStyles = scopedStyles('../../../src/pages/admin/AdminOperationsPage.vue');
const reviewStyles = scopedStyles('../../../src/pages/admin/AdminProblemReviewsPage.vue');
const userStyles = scopedStyles('../../../src/pages/admin/AdminUsersPage.vue');
const tableStyles = componentSource('../../../src/pages/admin/AdminTable.vue');
const reviewSource = componentSource('../../../src/pages/admin/AdminProblemReviewsPage.vue');
const usersSource = componentSource('../../../src/pages/admin/AdminUsersPage.vue');

describe('admin visual shell contract', () => {
  it('uses semantic inline icons instead of numbered navigation badges', () => {
    expect(layoutSource.match(/class="admin-nav__icon"/g)).toHaveLength(3);
    expect(layoutSource.match(/<svg[\s\S]*?aria-hidden="true"/g)).toHaveLength(3);
    expect(layoutSource).not.toContain('>01</span>');
    expect(layoutSource).not.toContain('>02</span>');
    expect(layoutSource).not.toContain('>03</span>');
  });

  it('uses the scoped blue-gray shell and Alethicode active navigation treatment', () => {
    expect(layoutStyles).toMatch(/--admin-shell-bg:\s*linear-gradient\(180deg, #f8fbff 0%, #f3f7fc 100%\);/);
    expect(layoutStyles).toContain('--admin-panel-radius: 16px;');
    expect(layoutStyles).toContain('--admin-panel-shadow: 0 6px 18px rgb(15 23 42 / 8%);');
    expect(layoutStyles).toContain('--admin-table-header-bg: #eef4fb;');
    expect(layoutStyles).toContain('--admin-table-hover-bg: #f8fbff;');
    expect(layoutStyles).toMatch(/\.admin-nav__link\s*\{[^}]*font-size:\s*15px;/);
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

  it('keeps the global pixel button effect out of the admin interface', () => {
    expect(layoutStyles).toMatch(/\.admin-shell :deep\(button\)\s*\{[^}]*box-shadow:\s*none;/);
    expect(layoutStyles).toMatch(/\.admin-shell :deep\(button:active\)\s*\{[^}]*transform:\s*none;[^}]*box-shadow:\s*none;/);
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
    expect(panelSource).toContain("'admin-panel--with-footer': Boolean($slots.footer)");
    expect(panelStyles).toMatch(/\.admin-panel--with-footer \.admin-panel__body\s*\{[^}]*padding-bottom:\s*0;/);
    expect(panelStyles).toMatch(/\.admin-panel__footer\s*\{[^}]*padding:\s*18px 22px 22px;/);
    expect(panelStyles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.admin-panel__footer\s*\{[^}]*padding:\s*18px 16px 22px;/);
    expect(panelStyles).toMatch(/@media \(max-width: 640px\) \{[\s\S]*?\.admin-panel__tools\s*\{[^}]*flex:\s*0 1 auto;/);

    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\s*\{[^}]*background:\s*var\(--admin-table-header-bg, #eef4fb\);/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\.admin-table__sticky\s*\{[^}]*position:\s*sticky;[^}]*right:\s*0;/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table td\s*\{[^}]*padding:\s*10px 16px;/);
    expect(tableStyles).toContain('position: sticky;');
    expect(reviewSource).toContain('class="review-table__action admin-table__sticky"');
    expect(usersSource).toContain('class="admin-table__sticky"');
    expect(reviewSource).toContain(':min-width="1152"');
    expect(reviewStyles).toMatch(/\.review-table td:nth-child\(1\),[\s\S]*?white-space:\s*nowrap;/);
    expect(reviewStyles).toMatch(/\.review-table td:nth-child\(3\),\s*\.review-table td:nth-child\(5\),\s*\.review-table td:nth-child\(7\)\s*\{[^}]*text-align:\s*center;/);
    expect(reviewStyles).toContain('.review-table__action { width: 140px;');
    expect(reviewStyles).toContain('.review-row-actions .admin-button');
    expect(reviewStyles).toContain('background: var(--admin-table-hover-bg);');
    expect(layoutStyles).toContain('.admin-status--muted');
    expect(operationsStyles).toMatch(/\.admin-operations__panels\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/);
    expect(operationsStyles).not.toContain('.admin-page-heading');
    expect(operationsStyles).toContain('@media (max-width: 900px)');
    expect(operationsStyles).toContain('grid-template-columns: 1fr;');
  });

  it('keeps the user table on the same row and cell spacing as the review table', () => {
    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\s*\{[^}]*height:\s*var\(--table-header-height, 44px\);[^}]*padding:\s*0 16px;/);
    expect(tableStyles).toMatch(/\.admin-table-scroll\s*\{[^}]*border-radius:\s*var\(--admin-panel-radius, 16px\);/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table th\s*\{[^}]*text-align:\s*center;/);
    expect(tableStyles).toMatch(/\.admin-table-scroll > table td\s*\{[^}]*height:\s*var\(--table-row-height, 52px\);[^}]*padding:\s*10px 16px;/);
    expect(tableStyles).toContain('-webkit-overflow-scrolling: touch;');
    expect(tableStyles).toContain('overscroll-behavior-x: contain;');
    expect(usersSource).toContain(':min-width="820"');
    expect(userStyles).toMatch(/\.admin-users__table td\.admin-users__readable\s*\{[^}]*overflow-wrap:\s*anywhere;[^}]*white-space:\s*normal;/);
    expect(componentSource('../../../src/pages/admin/AdminPagination.vue'))
      .toMatch(/\.admin-pagination\s*\{[^}]*justify-content:\s*center;/);
    expect(componentSource('../../../src/pages/admin/AdminPagination.vue'))
      .toMatch(/\.admin-pagination__buttons \.admin-button\s*\{[^}]*width:\s*34px;[^}]*height:\s*34px;[^}]*min-height:\s*34px;/);
    expect(componentSource('../../../src/pages/admin/AdminPagination.vue'))
      .toMatch(/\.admin-pagination__page\s*\{[^}]*min-width:\s*34px;/);
  });

  it('provides a shared compact toolbar and consistent admin controls', () => {
    expect(panelStyles).toContain('.admin-panel__toolbar');
    expect(panelStyles).toMatch(/\.admin-panel__header h2\s*\{[^}]*font-size:\s*20px;/);
    expect(layoutStyles).toContain(':deep(.admin-control)');
    expect(layoutStyles).toContain(':deep(.admin-button)');
    expect(layoutStyles).toContain(':deep(.admin-status)');
    expect(layoutStyles).toContain(':deep(.admin-dialog)');
    expect(userStyles).toContain('.admin-users__filters');
    expect(reviewStyles).toContain('.review-toolbar');
    expect(usersSource).toContain('admin-control');
    expect(reviewSource).toContain('admin-control');
    expect(usersSource).toContain('admin-status');
    expect(reviewSource).toContain('admin-status');
  });

  it('keeps filters and pagination in their shared panel regions', () => {
    expect(usersSource).toContain('<template #toolbar>');
    expect(usersSource).toMatch(/<template #footer>[\s\S]*?AdminPagination/);
    expect(reviewSource).toContain('<template #toolbar>');
    expect(reviewSource).toMatch(/<template #footer>[\s\S]*?AdminPagination/);
    expect(reviewSource).toContain('class="admin-dialog review-dialog"');
  });
});

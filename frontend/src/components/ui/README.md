# Teacher management UI

Button, Card, Table and Dialog source copied from the official [shadcn-vue](https://github.com/unovue/shadcn-vue) `new-york` registry on 2026-11-05. The upstream MIT license is retained in `LICENSE.shadcn-vue`.

Local adaptations: default Button type is `button`, Lucide uses `@lucide/vue`, dialog close text is Chinese, and portal overlays use an admin-specific class. Tailwind utilities and theme values are scoped to `.teacher-page` / `.teacher-dialog` in `features/teacherTheme.css`, without Preflight. Shared student/game CSS must not import this theme. Native form controls remain semantic browser controls and use admin-scoped styling.

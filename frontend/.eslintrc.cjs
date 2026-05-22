module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint', 'vue'],
  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended', 'plugin:@typescript-eslint/recommended'],
  rules: {
    '@typescript-eslint/no-explicit-any': 'error',
    'no-restricted-imports': [
      'error',
      {
        paths: [
          {
            name: 'phaser',
            message: '业务层禁止直接 import Phaser；请通过 src/game/adapters 隔离。',
          },
        ],
      },
    ],
    'vue/multi-word-component-names': 'off',
  },
  overrides: [
    {
      // game adapter + scene 层是 Phaser 与业务的隔离边界，允许直接 import phaser；
      // 业务层（composables / stores / components）仍走 no-restricted-imports 拦截。
      files: ['src/game/adapters/**/*.ts', 'src/game/scenes/**/*.ts'],
      rules: {
        '@typescript-eslint/no-explicit-any': 'warn',
        'no-restricted-imports': 'off',
      },
    },
    {
      // useAudio 是业务层与 Phaser sound manager 唯一适配点（详见文件顶部注释），
      // 只对它单独放行 phaser 类型导入，不放行 any 扩散。
      files: ['src/composables/useAudio.ts'],
      rules: {
        'no-restricted-imports': 'off',
      },
    },
  ],
};

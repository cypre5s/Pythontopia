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
      files: ['src/game/adapters/**/*.ts'],
      rules: {
        '@typescript-eslint/no-explicit-any': 'warn',
        'no-restricted-imports': 'off',
      },
    },
  ],
};

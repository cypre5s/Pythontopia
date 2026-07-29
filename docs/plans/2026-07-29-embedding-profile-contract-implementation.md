# Embedding Profile Contract Implementation Plan

**Goal:** Make the configured embedding dimension executable across the RAG selector and Java HTTP boundary, eliminating the production 1024/2048 drift without a database migration.

**Architecture:** Keep PostgreSQL `halfvec(2048)` as the storage contract. Make the Python embedder selector honor the configured dimension even in explicit hash mode, and make the Java client reject `/embed` responses whose declared dimension disagrees with their vectors.

**Tech Stack:** Python 3.12, FastAPI, Pydantic 2, Pytest, Java 21, Spring Boot, Maven, JUnit 5.

---

### Task 1: Lock the RAG selector to the deployment profile

**Files:**
- Modify: `services/pythontopia-rag/tests/test_zhipu_embedder.py`
- Modify: `services/pythontopia-rag/pythontopia_rag/index/embedder.py`
- Modify: `services/pythontopia-rag/README.md`

**RED**

- Assert explicit `hash` with `EMBEDDING_DIM=2048` returns `hash-bow-2048` and 2048-value vectors.
- Assert `PYT_RAG_EMBEDDING_DIM` takes precedence over `EMBEDDING_DIM`.
- Assert zero/negative/invalid dimensions fail during `select_embedder()`.

Run:

```bash
cd services/pythontopia-rag
/home/cypress/Pythontopia/services/pythontopia-rag/.venv/bin/python \
  -m pytest -q tests/test_zhipu_embedder.py
```

**GREEN**

- Add one positive-integer dimension parser.
- Reuse it in explicit hash and zhipu-fallback selection.
- Keep 1024 only as the no-configuration offline default for explicit hash mode.

### Task 2: Enforce declared dimension at the Java boundary

**Files:**
- Modify: `backend/src/test/java/com/pythontopia/learner/memory/EmbeddingClientTest.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/memory/RagEmbeddingClient.java`

**RED**

Return `dim=3` with a two-value vector and assert the corresponding result is empty.

Run:

```bash
cd backend
mvn --settings .mvn/settings.build.xml -Dtest=EmbeddingClientTest test
```

**GREEN**

- Reject non-positive declared dimensions.
- Reject each vector whose size differs from the declared dimension.
- Preserve response alignment and all existing fail-soft behavior.

### Task 3: Verify and close Slice 2

Run:

```bash
cd services/pythontopia-rag
/home/cypress/Pythontopia/services/pythontopia-rag/.venv/bin/python -m pytest -q
/home/cypress/Pythontopia/services/pythontopia-rag/.venv/bin/python -m ruff check .
/home/cypress/Pythontopia/services/pythontopia-rag/.venv/bin/python -m ruff format --check .
/home/cypress/Pythontopia/services/pythontopia-rag/.venv/bin/python -m mypy pythontopia_rag

cd backend
mvn --settings .mvn/settings.build.xml test

git diff --check
```

Update:

- `docs/plans/2026-07-29-incremental-refactoring-worklog.md`
- `docs/project/CHANGELOG.md`

Commit only Slice 2 files with:

```bash
git commit -m "refactor(embedding): enforce configured vector profile"
```

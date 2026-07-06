from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class Fold:
    index: int
    train: list[dict[str, Any]]
    validation: list[dict[str, Any]]


def kfold(cases: list[dict[str, Any]], k: int, seed: int) -> list[Fold]:
    if k <= 1:
        raise ValueError("k must be greater than 1")
    if k > len(cases):
        raise ValueError("k cannot exceed number of cases")
    shuffled = list(cases)
    random.Random(seed).shuffle(shuffled)
    folds: list[Fold] = []
    for index in range(k):
        validation = [case for position, case in enumerate(shuffled) if position % k == index]
        validation_ids = {case.get("id") for case in validation}
        train = [case for case in shuffled if case.get("id") not in validation_ids]
        folds.append(Fold(index=index, train=train, validation=validation))
    return folds

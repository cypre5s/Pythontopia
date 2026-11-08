import re


def normalize_student_id(value: str) -> str:
    value = value.strip()
    if not re.fullmatch(r"[0-9]{1,32}", value):
        raise ValueError("学号须为1至32位数字")
    return value

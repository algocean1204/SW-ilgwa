#!/usr/bin/env python3
"""아티팩트 → portfolio-showcase MSW 정적 store JSON 변환."""
from __future__ import annotations

import importlib.util
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
PORTFOLIO_ROOT = SCRIPT_DIR.parent
SHOWCASE_ROOT = SCRIPT_DIR.parent.parent / "_showcase_seed"
ARTIFACTS_ROOT = SHOWCASE_ROOT / "artifacts"
INFRA_SCRIPTS = SCRIPT_DIR.parent.parent / "infra" / "scripts"
OUT_PATH = SCRIPT_DIR.parent / "src" / "mocks" / "data" / "showcase-store.json"
AUDIO_MANIFEST_PATH = SCRIPT_DIR.parent / "src" / "mocks" / "data" / "audio-manifest.json"

if str(INFRA_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(INFRA_SCRIPTS))
if str(SHOWCASE_ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(SHOWCASE_ROOT / "scripts"))

_spec = importlib.util.spec_from_file_location(
    "inject_showcase",
    SHOWCASE_ROOT / "scripts" / "inject_showcase.py",
)
if _spec is None or _spec.loader is None:
    raise ImportError("inject_showcase 로드 실패")
inject = importlib.util.module_from_spec(_spec)
sys.modules["inject_showcase"] = inject
_spec.loader.exec_module(inject)

SUBJECTS = {
    "python": {"title": "파이썬 완전정복", "subject": "개발", "key": "python"},
    "rust": {"title": "Rust언어 완전정복", "subject": "개발", "key": "rust"},
    "os": {"title": "운영체제 완전 정복", "subject": "개발", "key": "os"},
}

NOW = datetime.now(timezone.utc).isoformat()
DEMO_USER = {
    "id": "usr_demo_portfolio",
    "email": "demo@portfolio.local",
    "nickname": "데모 학습자",
    "onboardingCompleted": True,
    "subscriptionStatus": "FREE",
}
DEMO_TUTOR = {
    "tutorId": "tut_demo_portfolio",
    "name": "시연 튜터",
    "tagline": "포트폴리오 데모 튜터",
    "profileImageUrl": "default-tutor.svg",
    "voiceSampleUrl": "",
}


def stable_id(prefix: str, *parts: str | int) -> str:
    return f"{prefix}_{'_'.join(str(p) for p in parts)}"


def load_audio_manifest() -> dict[tuple[str, int, int], dict[str, Any]]:
    """audio-manifest.json → (subject, chapter_index, slide_idx) → {path, duration_sec}."""
    if not AUDIO_MANIFEST_PATH.is_file():
        return {}
    entries = json.loads(AUDIO_MANIFEST_PATH.read_text(encoding="utf-8"))
    mapping: dict[tuple[str, int, int], dict[str, Any]] = {}
    for entry in entries:
        key = (
            str(entry["subject"]),
            int(entry["chapter_index"]),
            int(entry["slide_idx"]),
        )
        mapping[key] = {
            "path": str(entry["path"]),
            "duration_sec": float(entry["duration_sec"]),
        }
    return mapping


AUDIO_MANIFEST = load_audio_manifest()


def bundled_audio_url(subject: str, chapter_index: int, slide_idx: int) -> str | None:
    """public/ 에 번들된 슬라이드 오디오 상대 경로. 런타임에 BASE_URL을 앞에 붙인다."""
    entry = AUDIO_MANIFEST.get((subject, chapter_index, slide_idx))
    return entry["path"] if entry else None


def manifest_duration_sec(subject: str, chapter_index: int, slide_idx: int) -> float:
    """매니페스트 duration_sec. 없으면 0."""
    entry = AUDIO_MANIFEST.get((subject, chapter_index, slide_idx))
    return float(entry["duration_sec"]) if entry else 0.0


def api_ts() -> str:
    return NOW


def to_attempt_question(q: dict[str, Any], exam_key: str, order: int) -> dict[str, Any]:
    template_id = q["template_id"]
    options_json = q.get("options_json") or []
    meta = q.get("grading_metadata_json") or {}

    options = [{"label": o["label"], "text": o["text"]} for o in options_json]
    blank_count = 0
    ordering_items: list[str] = []
    matching_left: list[str] = []
    matching_right: list[str] = []

    if template_id == "ko_fill_blank":
        blank_count = q["stem"].count("___") or len(meta.get("blank_answers", [])) or 1
    elif template_id == "ko_ordering":
        ordering_items = list(meta.get("correct_ordering") or meta.get("ordering_items") or [])
        if not ordering_items and isinstance(q.get("correct_answer"), str):
            ordering_items = [s.strip() for s in q["correct_answer"].split("|") if s.strip()]
    elif template_id == "ko_matching":
        pairs = meta.get("matching_pairs") or []
        for p in pairs:
            matching_left.append(p.get("left", ""))
            matching_right.append(p.get("right", ""))

    return {
        "questionId": stable_id("q", exam_key, f"{order:02d}"),
        "templateId": template_id,
        "displayOrder": order,
        "stem": q["stem"],
        "points": float(q.get("points", 5)),
        "options": options,
        "blankCount": blank_count,
        "orderingItems": ordering_items,
        "matchingLeftItems": matching_left,
        "matchingRightItems": matching_right,
        "_correctAnswer": q.get("correct_answer"),
        "_explanation": q.get("explanation", ""),
        "_gradingMetadata": meta,
        "_submitted": q.get("submitted"),
        "_isCorrect": q.get("is_correct"),
        "_score": q.get("score"),
        "_gradingMode": q.get("grading_mode", "DETERMINISTIC"),
        "_feedback": q.get("feedback", ""),
        "_rubric": q.get("rubric_breakdown_json") or [],
    }


def build_exam(exam_doc: dict[str, Any], course_id: str, subject: str) -> dict[str, Any]:
    exam_idx = int(exam_doc["exam_index"])
    exam_key = f"{subject}_{exam_idx:02d}"
    mex_id = stable_id("mex", exam_key)
    attempt_id = stable_id("eat", exam_key)
    status = exam_doc.get("status", "IN_PROGRESS")
    questions = exam_doc["questions"]
    total_points = int(exam_doc.get("total_points", sum(q.get("points", 5) for q in questions)))

    attempt_questions = [
        to_attempt_question(q, exam_key, int(q["display_order"])) for q in questions
    ]

    public_questions = []
    for aq in attempt_questions:
        public_q = {k: v for k, v in aq.items() if not k.startswith("_")}
        public_questions.append(public_q)

    list_status = "COMPLETED" if status == "GRADED" else "READY"
    mock_status = "COMPLETED" if status == "GRADED" else "READY"

    exam_entry = {
        "mockExam": {
            "id": mex_id,
            "title": exam_doc["title"],
            "examType": "final",
            "questionCount": len(questions),
            "difficulty": exam_doc.get("difficulty", "hard"),
            "timeLimit": int(exam_doc.get("time_limit", 45)),
            "status": mock_status,
            "totalPoints": total_points,
            "createdAt": NOW,
            "attemptId": attempt_id,
        },
        "attempt": {
            "attemptId": attempt_id,
            "mockExamId": mex_id,
            "courseId": course_id,
            "status": status,
            "questions": attempt_questions,
            "publicQuestions": public_questions,
        },
    }

    if status == "GRADED":
        result_doc = exam_doc.get("result", {})
        analysis = result_doc.get("analysis", {})
        results = []
        for aq in attempt_questions:
            submitted = aq.get("_submitted") or {}
            my_answer = format_submitted_display(aq, submitted)
            review_answer = format_review_answer(aq)
            results.append({
                "questionId": aq["questionId"],
                "templateId": aq["templateId"],
                "displayOrder": aq["displayOrder"],
                "stem": aq["stem"],
                **({"displayOptions": aq["options"]} if aq["options"] else {}),
                "myAnswer": my_answer,
                "reviewAnswer": review_answer,
                "score": float(aq.get("_score") or 0),
                "maxScore": float(aq["points"]),
                "correct": bool(aq.get("_isCorrect")),
                "gradingMode": aq.get("_gradingMode") or "DETERMINISTIC",
                "feedback": aq.get("_feedback") or "",
                "reviewSolution": aq.get("_explanation") or "",
                "rubricBreakdown": [
                    {
                        "criterion": r.get("criterion", ""),
                        "score": float(r.get("score", 0)),
                        "maxScore": float(r.get("max_score", 0)),
                        "reason": r.get("reason", ""),
                    }
                    for r in (aq.get("_rubric") or [])
                ],
                "needsManualReview": False,
            })
        exam_entry["gradedResult"] = {
            "totalScore": float(result_doc.get("total_score", 0)),
            "maxScore": float(total_points),
            "percentage": float(result_doc.get("percentage", 0)),
            "passed": bool(result_doc.get("passed", False)),
            "analysis": analysis.get("summary") if isinstance(analysis, dict) else str(analysis),
            "weakTopics": analysis.get("weakTopics", []) if isinstance(analysis, dict) else [],
            "results": results,
        }

    exam_entry["listItem"] = exam_entry["mockExam"]
    return exam_entry


def format_submitted_display(q: dict[str, Any], submitted: dict[str, Any]) -> str:
    tid = q["templateId"]
    if tid in ("ko_multiple_choice_4", "ko_multiple_choice_5", "ko_true_false"):
        label = submitted.get("label", "")
        for opt in q["options"]:
            if opt["label"] == label:
                return f"{label}. {opt['text']}" if opt.get("text") else label
        return str(label)
    if "text" in submitted:
        return str(submitted["text"])
    if "blanks" in submitted:
        return ", ".join(str(b) for b in submitted["blanks"])
    if "order" in submitted:
        return " → ".join(str(x) for x in submitted["order"])
    if "pairs" in submitted:
        pairs = submitted["pairs"]
        return "; ".join(f"{k}→{v}" for k, v in pairs.items())
    return json.dumps(submitted, ensure_ascii=False)


def format_review_answer(q: dict[str, Any]) -> str:
    ca = q.get("_correctAnswer")
    if ca is None:
        return ""
    tid = q["templateId"]
    if tid in ("ko_multiple_choice_4", "ko_multiple_choice_5"):
        for opt in q["options"]:
            if opt["label"] == ca:
                return f"{ca}. {opt['text']}"
        return str(ca)
    if tid == "ko_true_false":
        return str(ca)
    if tid == "ko_ordering" and isinstance(ca, str):
        return " → ".join(s.strip() for s in ca.split("|"))
    return str(ca)


def build_course(subject: str, meta: dict[str, str]) -> dict[str, Any]:
    course_id = stable_id("crs", subject)
    chapters_meta = inject.load_chapter_artifacts(subject)
    exams_raw = inject.load_exam_artifacts(subject)

    chapters: list[dict[str, Any]] = []
    all_notes: list[dict[str, Any]] = []
    all_assignments: list[dict[str, Any]] = []

    for ch_idx, (chapter_meta, content_doc) in enumerate(chapters_meta, start=1):
        ch_key = f"{subject}_{ch_idx:02d}"
        chapter_id = stable_id("ch", ch_key)
        chapter_name = chapter_meta.get("chapter_name", f"{ch_idx}장")
        content_by_idx = inject._content_html_by_idx(content_doc)

        slides = []
        quizzes = []
        quiz_results = []

        for slide in chapter_meta.get("slides", []):
            slide_idx = int(slide["slide_idx"])
            slide_id = stable_id("sl", ch_key, slide_idx)
            raw_html = content_by_idx.get(slide_idx, "")
            content = inject.render_slide_content(subject, raw_html) if raw_html else ""

            audio_url = bundled_audio_url(subject, ch_idx, slide_idx)
            slides.append({
                "slideId": slide_id,
                "slideIdx": slide_idx,
                "title": slide.get("title", ""),
                "content": content,
                "audioUrl": audio_url,
                "durationSec": manifest_duration_sec(subject, ch_idx, slide_idx),
            })

            quiz_data = slide.get("quiz", {})
            if quiz_data:
                quiz_id = stable_id("qz", ch_key, slide_idx)
                options = quiz_data.get("options", [])
                correct = int(quiz_data.get("correct_option", 0))
                quizzes.append({
                    "quizId": quiz_id,
                    "quizIdx": slide_idx,
                    "slideId": slide_id,
                    "questionText": quiz_data.get("question_text", ""),
                    "options": options,
                })
                quiz_results.append({
                    "quizId": quiz_id,
                    "selectedOption": correct,
                    "correctOption": correct,
                    "isCorrect": True,
                    "explanation": quiz_data.get("explanation", ""),
                })

        note = chapter_meta.get("note", {})
        note_id = stable_id("nte", ch_key)
        note_content = inject.format_note_content(note)
        note_title = f"{chapter_name} 핵심 노트"
        note_resp = {
            "id": note_id,
            "title": note_title,
            "content": note_content,
            "type": "ai_auto",
            "slideIndex": None,
            "timestamp": None,
            "starred": False,
            "createdAt": NOW,
        }
        all_notes.append({
            **note_resp,
            "courseId": course_id,
            "courseName": meta["title"],
            "subject": meta["subject"],
            "chapterId": chapter_id,
            "chapterTitle": chapter_name,
            "updatedAt": NOW,
        })

        assignment = chapter_meta.get("assignment", {})
        asg_id = stable_id("asg", ch_key)
        steps = assignment.get("steps", [])
        all_assignments.append({
            "id": asg_id,
            "title": assignment.get("title", ""),
            "description": inject.format_assignment_description(assignment),
            "status": "active",
            "dueDate": NOW,
            "totalQuestions": len(steps),
            "maxScore": 100,
            "questions": [{"questionId": i + 1, "content": s} for i, s in enumerate(steps)],
            "previousAnswer": None,
            "createdAt": NOW,
            "chapterId": chapter_id,
            "listItem": {
                "id": asg_id,
                "title": assignment.get("title", ""),
                "status": "active",
                "dueDate": NOW,
                "totalQuestions": len(steps),
                "maxScore": 100,
                "createdAt": NOW,
            },
        })

        chapters.append({
            "id": chapter_id,
            "order": ch_idx,
            "title": chapter_name,
            "description": chapter_meta.get("key_topics", ""),
            "estimatedMinutes": 45,
            "status": "COMPLETED",
            "slideCount": 8,
            "completedSlides": 8,
            "completedAt": NOW,
            "keyTopics": [t.strip() for t in chapter_meta.get("key_topics", "").split(",") if t.strip()],
            "bookmarks": [],
            "xpEarned": 100,
            "slides": slides,
            "quizzes": quizzes,
            "quizResult": {
                "totalQuestions": len(quizzes),
                "correctCount": len(quizzes),
                "score": 100,
                "results": quiz_results,
            },
            "notes": [note_resp],
        })

    exams = [build_exam(ex, course_id, subject) for ex in exams_raw]

    return {
        "id": course_id,
        "title": meta["title"],
        "subject": meta["subject"],
        "tutorId": DEMO_TUTOR["tutorId"],
        "tutorName": DEMO_TUTOR["name"],
        "tutorAvatarUrl": DEMO_TUTOR["profileImageUrl"],
        "status": "COMPLETED",
        "sourceType": "topic",
        "mode": "standard",
        "progress": 100,
        "totalLessons": 10,
        "completedLessons": 10,
        "lastStudiedAt": NOW,
        "createdAt": NOW,
        "chapters": chapters,
        "exams": exams,
        "notes": all_notes,
        "assignments": all_assignments,
    }


def main() -> None:
    courses = [build_course(sub, SUBJECTS[sub]) for sub in ("python", "rust", "os")]
    store = {
        "generatedAt": NOW,
        "user": DEMO_USER,
        "tutor": DEMO_TUTOR,
        "accessToken": "demo-portfolio-access-token",
        "courses": courses,
        "coins": 500,
        "subscription": {"status": "inactive", "planCode": "free"},
    }
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(store, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT_PATH} ({OUT_PATH.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
"""모델 캡션이 모두 실패했을 때 쓰는 Jinja2 템플릿 캡션."""
from jinja2 import Template

# 이름·약점·마감이 있을 때만 해당 구절을 넣는다. 이름이 없으면 호칭을 뺀다.
_TMPL = Template(
    "{% if name %}{{ name }}아, {% endif %}"
    "오늘 {{ assignment }} 같이 해보자! "
    "{% if weakness %}{{ weakness }} 부분만 한 번만 더 짚어주면 돼. {% endif %}"
    "{% if deadline %}{{ deadline }}까지야, 할 수 있어!{% else %}할 수 있어!{% endif %}"
)


def render_jinja_caption(
    *, student_name, assignment_name, weakness=None, deadline=None
) -> str:
    """Jinja2 템플릿으로 폴백 캡션을 만든다.

    100자를 넘으면 99자에서 자르고 말줄임표를 붙인다.
    """
    text = _TMPL.render(
        name=student_name,
        assignment=assignment_name,
        weakness=weakness,
        deadline=deadline,
    ).strip()
    if len(text) > 100:
        text = text[:99] + "…"
    return text

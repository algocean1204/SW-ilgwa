(() => {
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const links = [...document.querySelectorAll('nav a[href^="#"]')];
  const sections = links
    .map((a) => document.querySelector(a.getAttribute("href")))
    .filter(Boolean);

  const mark = (id) => {
    for (const a of links) {
      const on = a.getAttribute("href") === `#${id}`;
      if (on) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    }
  };

  if (!("IntersectionObserver" in window) || sections.length === 0) return;

  const io = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (visible?.target?.id) mark(visible.target.id);
    },
    { rootMargin: "-20% 0px -65% 0px", threshold: [0, 0.25, 0.6] }
  );

  for (const el of sections) io.observe(el);

  if (reduce) return;

  for (const a of links) {
    a.addEventListener("pointerdown", () => {
      a.style.opacity = "0.65";
    });
    a.addEventListener("pointerup", () => {
      a.style.opacity = "";
    });
    a.addEventListener("pointercancel", () => {
      a.style.opacity = "";
    });
  }
})();

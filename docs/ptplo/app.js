(() => {
  const stage = document.getElementById("stage");
  const btn = document.getElementById("export-pdf");
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const fit = () => {
    if (!stage) return;
    const sheet = stage.querySelector(".sheet");
    if (!sheet) return;
    stage.style.transform = "";
    stage.style.marginBottom = "";
    const raw = sheet.offsetWidth;
    const scale = Math.min(1, (window.innerWidth - 24) / raw);
    if (scale >= 0.999) return;
    stage.style.transform = `scale(${scale})`;
    stage.style.marginBottom = `${Math.round(stage.scrollHeight * (scale - 1))}px`;
  };

  const printFit = () => {
    if (!stage) return;
    stage.style.transform = "";
    stage.style.marginBottom = "";
  };

  btn?.addEventListener("pointerdown", () => {
    btn.style.opacity = "0.7";
  });
  btn?.addEventListener("pointerup", () => {
    btn.style.opacity = "";
  });
  btn?.addEventListener("pointercancel", () => {
    btn.style.opacity = "";
  });
  btn?.addEventListener("click", () => {
    printFit();
    window.print();
  });

  window.addEventListener("beforeprint", printFit);
  window.addEventListener("afterprint", fit);
  window.addEventListener("resize", () => {
    if (!reduce) fit();
  });

  fit();
})();

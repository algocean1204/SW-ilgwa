(() => {
  const stage = document.getElementById("stage");
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

  window.addEventListener("beforeprint", printFit);
  window.addEventListener("afterprint", fit);
  window.addEventListener("resize", () => {
    if (!reduce) fit();
  });

  fit();
})();

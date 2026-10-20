/* Bengali script → everyday Banglish (the way people type Bengali in English letters).
   আমাকে → amake, তোমার → tomar, কেমন আছো → kemon acho, করতে → korte, ভালো → bhalo.
   Rules: each consonant carries an "o" unless a vowel sign, a hasanta or a conjunct removes it;
   the "o" is dropped at the end of a word and in the middle of a word when it sits between
   a syllable that has a vowel and a consonant that has one (করতে → kor-te). */
(function (root) {
  const C = {
    "ক":"k","খ":"kh","গ":"g","ঘ":"gh","ঙ":"ng","চ":"ch","ছ":"ch","জ":"j","ঝ":"jh","ঞ":"n",
    "ট":"t","ঠ":"th","ড":"d","ঢ":"dh","ণ":"n","ত":"t","থ":"th","দ":"d","ধ":"dh","ন":"n",
    "প":"p","ফ":"f","ব":"b","ভ":"bh","ম":"m","য":"j","র":"r","ল":"l","শ":"sh","ষ":"sh",
    "স":"sh","হ":"h","\u09DC":"r","\u09DD":"rh","\u09DF":"y","ৎ":"t"
  };
  const NUKTA_FORM = { "ড":"r", "ঢ":"rh", "য":"y" };     // ড় ঢ় য় written as letter + dot below
  const V = { "অ":"o","আ":"a","ই":"i","ঈ":"i","উ":"u","ঊ":"u","ঋ":"ri","এ":"e","ঐ":"oi","ও":"o","ঔ":"ou" };
  const S = { "া":"a","ি":"i","ী":"i","ু":"u","ূ":"u","ৃ":"ri","ে":"e","ৈ":"oi","ো":"o","ৌ":"ou" };
  const HAS = "্", NUK = "়";
  const MARK = { "ং":"ng","ঃ":"h","ঁ":"" };
  const DIG = "০১২৩৪৫৬৭৮৯";

  /* everyday words that don't follow the general rule */
  const WORDS = { "কেন":"keno","যেন":"jeno","হল":"holo","হলো":"holo","বলল":"bollo","করল":"korlo","এল":"elo","মত":"moto","মতো":"moto",
    "কত":"koto","যত":"joto","তত":"toto","সব":"shob","তবে":"tobe","হয়তো":"hoyto","হয়তো":"hoyto","ভাল":"bhalo","বড়":"boro","বড়":"boro",
    "ছোট":"choto","ছোটো":"choto","বড়ো":"boro","বড়ো":"boro","সত্যি":"sotti","এক":"ek","দুই":"dui","তিন":"tin" };
  function word(w) {
    if (WORDS[w]) return WORDS[w];
    const syl = [];   // {cons: "kr", vowel: "a" | null(inherent) | "" (killed), indep: bool, tail: ""}
    let i = 0;
    while (i < w.length) {
      const ch = w[i];
      if (C[ch]) {
        let cons = C[ch], conj = false; i++;
        if (w[i] === NUK) { cons = NUKTA_FORM[ch] || cons; i++; }
        while (w[i] === HAS && C[w[i + 1]]) {             // conjunct: ক্র, স্ট, ন্য ...
          const nx = w[i + 1];
          if (ch === "স" && cons === "sh" && "কটতপফ".includes(nx)) cons = "s";     // স্কুল → skul, স্টেশন → steshon
          if (nx === "য") cons += cons.slice(-1);                    // ন্য → nn (dhonnobad), ত্য → tt (sotti)
          else if (nx === "ব" && cons.length) cons += "";             // ব-ফলা is silent: বিশ্ব → bishsho
          else cons += C[nx];
          conj = true; i += 2;
        }
        let vowel = null;
        if (w[i] === HAS) { vowel = ""; i++; }
        else if (S[w[i]]) { vowel = S[w[i]]; i++; }
        syl.push({cons, vowel, conj, letter: ch});
      } else if (V[ch]) { syl.push({cons: "", vowel: V[ch]}); i++; }
      else if (MARK[ch] !== undefined) { if (syl.length) syl[syl.length - 1].tail = (syl[syl.length - 1].tail || "") + MARK[ch]; i++; }
      else if (S[ch]) { syl.push({cons: "", vowel: S[ch]}); i++; }
      else { syl.push({raw: ch}); i++; }
    }
    // decide inherent vowels
    for (let k = 0; k < syl.length; k++) {
      const s = syl[k];
      if (s.raw !== undefined || s.vowel !== null) continue;
      const last = k === syl.length - 1 || syl[k + 1].raw !== undefined;
      if (last) {
        const prev = k > 0 ? syl[k - 1] : null;
        const verbLo = s.letter === "ল" && prev && (prev.vowel === "i" || prev.vowel === "e");   // ছিল → chilo, গেল → gelo
        s.vowel = (k === 0 || s.conj || s.letter === "হ" || verbLo) ? "o" : "";
        continue;
      }
      const prevHasVowel = k > 0 && syl[k - 1].raw === undefined && syl[k - 1].vowel !== "";
      const next = syl[k + 1];
      const nextHasVowel = next.raw === undefined && next.cons && next.vowel !== null && next.vowel !== "";
      s.vowel = (prevHasVowel && nextHasVowel && k > 0 && !s.conj) ? "" : "o";
    }
    return syl.map(s => s.raw !== undefined ? s.raw : s.cons + s.vowel + (s.tail || "")).join("");
  }

  function toBanglish(text) {
    if (!text || !/[ঀ-৿]/.test(text)) return text || "";
    return text
      .replace(/[০-৯]/g, d => String(DIG.indexOf(d)))
      .replace(/।/g, ".")
      .replace(/[ঀ-৿]+/g, word);
  }
  root.toBanglish = toBanglish;
})(typeof window !== "undefined" ? window : globalThis);

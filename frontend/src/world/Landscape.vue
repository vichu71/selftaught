<template>
  <svg class="landscape" data-testid="world-landscape" aria-hidden="true" viewBox="0 0 1280 800" preserveAspectRatio="xMidYMax slice" xmlns="http://www.w3.org/2000/svg">
    <circle class="ls-sun" data-part="sun" cx="1120" cy="130" r="64" />
    <g class="ls-cloud">
      <ellipse cx="220" cy="170" rx="90" ry="26" /><ellipse cx="290" cy="148" rx="58" ry="26" />
      <ellipse cx="820" cy="110" rx="80" ry="22" /><ellipse cx="868" cy="94" rx="48" ry="22" />
    </g>
    <path class="ls-hill ls-hill--far" data-part="hill" d="M0 640 C 220 570, 420 670, 640 620 S 1060 570, 1280 640 V800 H0Z" />
    <path class="ls-hill" data-part="hill" d="M0 720 C 260 670, 520 760, 760 710 S 1100 690, 1280 730 V800 H0Z" />
  </svg>
</template>

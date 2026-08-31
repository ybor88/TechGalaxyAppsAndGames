// Copyright (c) Roberto Di Flumeri

document.addEventListener('DOMContentLoaded', function () {
  // Mobile nav toggle
  var header = document.querySelector('.site-header');
  var navToggle = document.querySelector('.nav-toggle');
  if (navToggle && header) {
    navToggle.addEventListener('click', function () {
      header.classList.toggle('nav-open');
    });
    document.querySelectorAll('.main-nav a').forEach(function (link) {
      link.addEventListener('click', function () {
        header.classList.remove('nav-open');
      });
    });
  }

  // Accordion (FAQ)
  document.querySelectorAll('.accordion-header').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var item = btn.closest('.accordion-item');
      var body = item.querySelector('.accordion-body');
      var isOpen = item.classList.contains('open');

      document.querySelectorAll('.accordion-item.open').forEach(function (openItem) {
        if (openItem !== item) {
          openItem.classList.remove('open');
          openItem.querySelector('.accordion-body').style.maxHeight = null;
        }
      });

      if (isOpen) {
        item.classList.remove('open');
        body.style.maxHeight = null;
      } else {
        item.classList.add('open');
        body.style.maxHeight = body.scrollHeight + 'px';
      }
    });
  });

  // Contact form (front-end only placeholder — collega un vero invio quando avrai un backend/servizio email)
  var form = document.getElementById('contact-form');
  if (form) {
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var status = document.getElementById('form-status');
      var required = form.querySelectorAll('[required]');
      var valid = true;

      required.forEach(function (field) {
        if (!field.value.trim()) {
          valid = false;
        }
      });

      if (!valid) {
        status.textContent = 'Compila tutti i campi obbligatori.';
        status.className = 'form-status error';
        return;
      }

      status.textContent = 'Messaggio inviato correttamente. Ti risponderemo al più presto.';
      status.className = 'form-status success';
      form.reset();
    });
  }
});

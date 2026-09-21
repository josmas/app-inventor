---
layout: page
title: Docs
permalink: /
---

{%- assign docs = site.docs | sort: "order" %}
<ol>
{%- for doc in docs %}
  <li><a href="{{ doc.url | relative_url }}">{{ doc.title | escape }}</a></li>
{%- endfor %}
</ol>

# AISCO HR Job Satisfaction Survey — Forms blueprint

**Status:** Live form created in AISCO (published + OPEN anonymous run + public share).  
**Source:** `HUMAN RESOURCES DEPARTMENT english Translated.docx`  
**Company:** AISCO (`Angola Iron and Steel Corporation`)  
**Form ID:** `01a0f152-3026-7758-974e-59ddb98966cb`  
**Run ID:** `01a0f152-6c70-7463-bbff-eb666e6009f9`  
**Public fill URL:** https://forms.ravalgroups.com/s/5675a1cb215ea3812d8f2a10632144eca7d46e2c08c0bcac550c91a4d004404b

---

## Goal

Model the HR survey as proper form structure — especially **matrix/table Likert sections** — not flattened into many independent rating fields or open text. Ship **English + Portuguese** via form content locales.

---

## Languages (`meta`)

| Field | Value |
| --- | --- |
| `defaultLocale` | `en` |
| `contentLocales` | `["en", "pt"]` |

Amharic (`am`) and Oromiffa (`om`) are supported by the platform for other companies; AISCO does **not** ship those translations unless HR supplies copy.

---

## Form header (definition `meta`)

| Field | English (default) | Portuguese |
| --- | --- | --- |
| Issuing body title | `HUMAN RESOURCES DEPARTMENT` | `DEPARTAMENTO DE RECURSOS HUMANOS` |
| Introduction (WYSIWYG HTML) | See below | See below |
| Company brand | Logo when available; until then stylish **AISCO** code mark | same |

### Introduction — English

Dear Employee,

Thank you for participating in this survey. Your feedback is essential for us to understand your experience at AISCO and identify areas for improvement. Your responses will be treated confidentially and used exclusively for internal analysis purposes, with the aim of promoting a more positive work environment in line with the principles of personal development and fulfillment. This survey is completely anonymous. We do not ask you to write your name or personal identity at any time; you just need to tell us your age and gender. This survey will consume approximately 7 minutes of your time.

**Classification:**  
1 – Very Dissatisfied; 2 – Dissatisfied; 3 – Neutral; 4 – Satisfied; 5 – Very Satisfied

### Introduction — Portuguese

Caro(a) Colaborador(a),

Obrigado por participar nesta pesquisa. O seu feedback é essencial para compreendermos a sua experiência na AISCO e identificarmos áreas de melhoria. As suas respostas serão tratadas de forma confidencial e utilizadas exclusivamente para análise interna, com o objetivo de promover um ambiente de trabalho mais positivo, alinhado com os princípios de desenvolvimento e realização pessoal. Esta pesquisa é completamente anónima. Não pedimos o seu nome nem identidade pessoal em nenhum momento; basta indicar a idade e o género. Esta pesquisa demora aproximadamente 7 minutos.

**Classificação:**  
1 – Muito Insatisfeito(a); 2 – Insatisfeito(a); 3 – Neutro(a); 4 – Satisfeito(a); 5 – Muito Satisfeito(a)

---

## Question plan

### Page / block 1 — About you (demographics)

| Key | Type | Required | EN label | PT label |
| --- | --- | --- | --- | --- |
| `department` | SHORT_TEXT or DROPDOWN | yes | Department | Departamento |
| `working_time` | SHORT_TEXT | yes | Working time | Tempo de trabalho |
| `gender` | SINGLE_CHOICE | yes | Gender | Género |
| `functions` | SHORT_TEXT | no | Functions | Funções |
| `age` | SHORT_TEXT or NUMBER | no | Age (Optional) | Idade (opcional) |

Gender options (EN / PT): Female / Feminino · Male / Masculino · Prefer not to say / Prefiro não dizer · Other / Outro  

“Total satisfaction value” from the Word header is **not** a respondent field (HR scoring after the fact).

### Page / block 2 — Overall satisfaction & work environment

| Key | Type | Configuration |
| --- | --- | --- |
| `overall_environment` | **MATRIX** | 5 statement rows × 1–5 Likert columns + comment row |

**Rows (full statements)**

| key | English | Portuguese |
| --- | --- | --- |
| `overall_satisfaction` | What is your overall level of satisfaction with working at AISCO? | Qual é o seu nível geral de satisfação com o trabalho na AISCO? |
| `work_environment` | How do you evaluate the work environment at AISCO in general? | Como avalia o ambiente de trabalho na AISCO em geral? |
| `work_purpose` | Do you feel that your work at AISCO has a purpose and contributes to the company's goals? | Sente que o seu trabalho na AISCO tem um propósito e contribui para os objetivos da empresa? |
| `colleagues` | How do you evaluate your relationship with colleagues? | Como avalia a sua relação com os colegas? |
| `boss` | How do you evaluate your relationship with the boss? | Como avalia a sua relação com o chefe? |

**Columns (EN / PT):** Very dissatisfied / Muito insatisfeito(a) · Dissatisfied / Insatisfeito(a) · Neutral / Neutro(a) · Satisfied / Satisfeito(a) · Very Satisfied / Muito satisfeito(a) (values 1–5)  
**Comment row:** Comment / Comentário

### Page / block 3 — Personal & professional development

| Key | Type |
| --- | --- |
| `development` | **MATRIX** (5 rows + comment) |

| key | English | Portuguese |
| --- | --- | --- |
| `development_opportunities` | Do you feel that you have opportunities for personal and professional development at AISCO? | Sente que tem oportunidades de desenvolvimento pessoal e profissional na AISCO? |
| `initiative_creativity` | Does AISCO encourage initiative, creativity and the search for new ideas? | A AISCO incentiva a iniciativa, a criatividade e a procura de novas ideias? |
| `continuous_learning` | Do you feel encouraged at AISCO to work on your mind, to seek continuous learning and to develop new skills, even if this is outside your immediate duties? | Sente-se incentivado(a) na AISCO a trabalhar a mente, a procurar aprendizagem contínua e a desenvolver novas competências, mesmo fora das suas funções imediatas? |
| `change_direction` | How do you evaluate opportunities at AISCO to start over or change direction in the company if new ideas or interests arise? | Como avalia as oportunidades na AISCO de recomeçar ou mudar de direção na empresa se surgirem novas ideias ou interesses? |
| `resilience` | Do you feel that AISCO values persistence, resilience and the ability to learn from mistakes (experience)? | Sente que a AISCO valoriza a persistência, a resiliência e a capacidade de aprender com os erros (experiência)? |

### Page / block 4 — Mindset & achievement

| Key | Type |
| --- | --- |
| `mindset` | **MATRIX** (3 rows + comment) |

| key | English | Portuguese |
| --- | --- | --- |
| `fulfillment` | Do you feel that AISCO promotes a mindset of job fulfillment, in which ideas are encouraged and achievements are valued? | Sente que a AISCO promove uma mentalidade de realização no trabalho, em que as ideias são encorajadas e as conquistas são valorizadas? |
| `long_term_vision` | Do you believe that AISCO helps you to develop a long-term vision and fight for your future, rather than focusing only on the present? | Acredita que a AISCO o(a) ajuda a desenvolver uma visão de longo prazo e a lutar pelo seu futuro, em vez de se focar apenas no presente? |
| `supervisor_development` | My supervisor has been a vehicle for my professional development. | O meu supervisor tem sido um veículo para o meu desenvolvimento profissional. |

### Page / block 5 — Safety & well-being

| Key | Type |
| --- | --- |
| `safety_wellbeing` | **MATRIX** (2 rows + comment) |

| key | English | Portuguese |
| --- | --- | --- |
| `feel_safe` | Do you feel safe and secure in your work environment at AISCO? | Sente-se seguro(a) no seu ambiente de trabalho na AISCO? |
| `wellbeing_concern` | Does AISCO show concern for your overall well-being (physical and mental) in the future, rather than focusing only on the present? | A AISCO demonstra preocupação com o seu bem-estar geral (físico e mental) no futuro, em vez de se focar apenas no presente? |

### Page / block 6 — Wage policies & incentives

| Key | Type |
| --- | --- |
| `wage_policies` | **MATRIX** (3 rows + comment) |

| key | English | Portuguese |
| --- | --- | --- |
| `current_salary` | I am satisfied with my current salary. | Estou satisfeito(a) com o meu salário atual. |
| `salary_policy_improve` | I feel that the salary policy could be improved. | Sinto que a política salarial poderia ser melhorada. |
| `skills_remuneration` | I feel that my contribution to the company could be remunerated according to my skills. | Sinto que a minha contribuição para a empresa poderia ser remunerada de acordo com as minhas competências. |

> **Polarity note:** Row `salary_policy_improve` is awkward on a satisfaction scale (high score may mean “agree it needs improvement”). Consider agree/disagree columns or rewording before go-live.

### Page / block 7 — Additional comments

| Key | Type | EN | PT |
| --- | --- | --- | --- |
| `additional_comments` | LONG_TEXT | Do you have any additional comments about your job satisfaction at AISCO, suggestions for improvements, or ideas you'd like to share, inspired by improvements to serve you better? | Tem comentários adicionais sobre a sua satisfação no trabalho na AISCO, sugestões de melhoria ou ideias que gostaria de partilhar, inspiradas em melhorias para o servir melhor? |

---

## Run / distribution (when creating later)

| Setting | Value |
| --- | --- |
| Creator | AISCO Forms admin (`aisco.admin`) |
| Respondent mode | **ANONYMOUS** (matches “do not write your name”) |
| Delivery | Public share link (not one-by-one invites) |
| Languages | Respondent picks **English** or **Português** (only creator-enabled locales) |
| Future | Bulk invite-all-employees for identified runs (noted, not in this build) |

---

## Answer storage (matrix)

Each MATRIX answer is one `jsonValue`:

```json
{
  "cells": { "row_key": 4 },
  "comment": "optional text"
}
```

Not one DB row per Likert cell as separate questions. Display labels are localized; stored keys/values are not.

---

## Platform prerequisites

- [x] `MATRIX` question type (API validation + JSON answers)  
- [x] Definition `meta.issuingBodyTitle` + `meta.introductionHtml`  
- [x] Designer: more types + matrix editor + WYSIWYG intro  
- [x] Fill UI: matrix table + branded header (company code placeholder)  
- [x] Form content i18n: `contentLocales` / `defaultLocale` / overlays; creator picks languages; respondent picker limited to those  
- [x] Create live AISCO form (published, OPEN anonymous run, public share)

---

## Open product notes

1. Wage row 2 polarity vs satisfaction scale — may rewrite to “agree/disagree” columns later.  
2. Anonymity vs department/gender/age — still quasi-identifiable in small teams; keep as HR policy choice.  
3. Company logo URL not stored yet — placeholder mark uses company code until logo pipeline exists.  
4. Portuguese copy translated from the English Word source (no original PT document on hand) — HR should review.  
5. Intro still mentions age/gender while claiming full anonymity — policy wording may need alignment.

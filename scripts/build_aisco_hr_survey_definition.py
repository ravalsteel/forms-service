#!/usr/bin/env python3
"""Build AISCO HR survey definition JSON (en + pt) from the approved blueprint."""

from __future__ import annotations

import json
import uuid


def uid() -> str:
    return str(uuid.uuid4())


LIKERT_COLS = [
    {
        "value": 1,
        "label": "Very dissatisfied",
        "i18n": {"pt": {"label": "Muito insatisfeito(a)"}},
    },
    {
        "value": 2,
        "label": "Dissatisfied",
        "i18n": {"pt": {"label": "Insatisfeito(a)"}},
    },
    {
        "value": 3,
        "label": "Neutral",
        "i18n": {"pt": {"label": "Neutro(a)"}},
    },
    {
        "value": 4,
        "label": "Satisfied",
        "i18n": {"pt": {"label": "Satisfeito(a)"}},
    },
    {
        "value": 5,
        "label": "Very Satisfied",
        "i18n": {"pt": {"label": "Muito satisfeito(a)"}},
    },
]


def matrix(key: str, label_en: str, label_pt: str, rows: list[tuple[str, str, str]], required: bool = True):
    qid = uid()
    return qid, {
        "id": qid,
        "key": key,
        "type": "MATRIX",
        "label": label_en,
        "required": required,
        "i18n": {"pt": {"label": label_pt}},
        "configuration": {
            "rows": [
                {
                    "id": uid(),
                    "key": rkey,
                    "label": en,
                    "i18n": {"pt": {"label": pt}},
                }
                for rkey, en, pt in rows
            ],
            "columns": LIKERT_COLS,
            "allowComment": True,
            "commentLabel": "Comment",
            "commentLabelI18n": {"pt": "Comentário"},
        },
    }


def text_q(key: str, typ: str, label_en: str, label_pt: str, required: bool, configuration=None):
    qid = uid()
    q = {
        "id": qid,
        "key": key,
        "type": typ,
        "label": label_en,
        "required": required,
        "i18n": {"pt": {"label": label_pt}},
    }
    if configuration is not None:
        q["configuration"] = configuration
    return qid, q


INTRO_EN = """<p>Dear Employee,</p>
<p>Thank you for participating in this survey. Your feedback is essential for us to understand your experience at AISCO and identify areas for improvement. Your responses will be treated confidentially and used exclusively for internal analysis purposes, with the aim of promoting a more positive work environment in line with the principles of personal development and fulfillment. This survey is completely anonymous. We do not ask you to write your name or personal identity at any time; you just need to tell us your age and gender. This survey will consume approximately 7 minutes of your time.</p>
<p><strong>Classification:</strong><br/>1 – Very Dissatisfied; 2 – Dissatisfied; 3 – Neutral; 4 – Satisfied; 5 – Very Satisfied</p>"""

INTRO_PT = """<p>Caro(a) Colaborador(a),</p>
<p>Obrigado por participar nesta pesquisa. O seu feedback é essencial para compreendermos a sua experiência na AISCO e identificarmos áreas de melhoria. As suas respostas serão tratadas de forma confidencial e utilizadas exclusivamente para análise interna, com o objetivo de promover um ambiente de trabalho mais positivo, alinhado com os princípios de desenvolvimento e realização pessoal. Esta pesquisa é completamente anónima. Não pedimos o seu nome nem identidade pessoal em nenhum momento; basta indicar a idade e o género. Esta pesquisa demora aproximadamente 7 minutos.</p>
<p><strong>Classificação:</strong><br/>1 – Muito Insatisfeito(a); 2 – Insatisfeito(a); 3 – Neutro(a); 4 – Satisfeito(a); 5 – Muito Satisfeito(a)</p>"""


def build() -> dict:
    questions = []
    components = []

    def add(qid, q):
        questions.append(q)
        components.append({"id": uid(), "type": "QUESTION", "questionId": qid})

    qid, q = text_q("department", "SHORT_TEXT", "Department", "Departamento", True)
    add(qid, q)
    qid, q = text_q("working_time", "SHORT_TEXT", "Working time", "Tempo de trabalho", True)
    add(qid, q)
    qid, q = text_q(
        "gender",
        "SINGLE_CHOICE",
        "Gender",
        "Género",
        True,
        {
            "options": [
                {
                    "value": "female",
                    "label": "Female",
                    "i18n": {"pt": {"label": "Feminino"}},
                },
                {
                    "value": "male",
                    "label": "Male",
                    "i18n": {"pt": {"label": "Masculino"}},
                },
                {
                    "value": "prefer_not",
                    "label": "Prefer not to say",
                    "i18n": {"pt": {"label": "Prefiro não dizer"}},
                },
                {
                    "value": "other",
                    "label": "Other",
                    "i18n": {"pt": {"label": "Outro"}},
                },
            ]
        },
    )
    add(qid, q)
    qid, q = text_q("functions", "SHORT_TEXT", "Functions", "Funções", False)
    add(qid, q)
    qid, q = text_q("age", "SHORT_TEXT", "Age (Optional)", "Idade (opcional)", False)
    add(qid, q)

    qid, q = matrix(
        "overall_environment",
        "Overall satisfaction and work environment at AISCO",
        "Satisfação geral e ambiente de trabalho na AISCO",
        [
            (
                "overall_satisfaction",
                "What is your overall level of satisfaction with working at AISCO?",
                "Qual é o seu nível geral de satisfação com o trabalho na AISCO?",
            ),
            (
                "work_environment",
                "How do you evaluate the work environment at AISCO in general?",
                "Como avalia o ambiente de trabalho na AISCO em geral?",
            ),
            (
                "work_purpose",
                "Do you feel that your work at AISCO has a purpose and contributes to the company's goals?",
                "Sente que o seu trabalho na AISCO tem um propósito e contribui para os objetivos da empresa?",
            ),
            (
                "colleagues",
                "How do you evaluate your relationship with colleagues?",
                "Como avalia a sua relação com os colegas?",
            ),
            (
                "boss",
                "How do you evaluate your relationship with the boss?",
                "Como avalia a sua relação com o chefe?",
            ),
        ],
    )
    add(qid, q)

    qid, q = matrix(
        "development",
        "Personal and professional development at AISCO",
        "Desenvolvimento pessoal e profissional na AISCO",
        [
            (
                "development_opportunities",
                "Do you feel that you have opportunities for personal and professional development at AISCO?",
                "Sente que tem oportunidades de desenvolvimento pessoal e profissional na AISCO?",
            ),
            (
                "initiative_creativity",
                "Does AISCO encourage initiative, creativity and the search for new ideas?",
                "A AISCO incentiva a iniciativa, a criatividade e a procura de novas ideias?",
            ),
            (
                "continuous_learning",
                "Do you feel encouraged at AISCO to work on your mind, to seek continuous learning and to develop new skills, even if this is outside your immediate duties?",
                "Sente-se incentivado(a) na AISCO a trabalhar a mente, a procurar aprendizagem contínua e a desenvolver novas competências, mesmo fora das suas funções imediatas?",
            ),
            (
                "change_direction",
                "How do you evaluate opportunities at AISCO to start over or change direction in the company if new ideas or interests arise?",
                "Como avalia as oportunidades na AISCO de recomeçar ou mudar de direção na empresa se surgirem novas ideias ou interesses?",
            ),
            (
                "resilience",
                "Do you feel that AISCO values persistence, resilience and the ability to learn from mistakes (experience)?",
                "Sente que a AISCO valoriza a persistência, a resiliência e a capacidade de aprender com os erros (experiência)?",
            ),
        ],
    )
    add(qid, q)

    qid, q = matrix(
        "mindset",
        "Mindset and achievement at AISCO",
        "Mentalidade e realização na AISCO",
        [
            (
                "fulfillment",
                "Do you feel that AISCO promotes a mindset of job fulfillment, in which ideas are encouraged and achievements are valued?",
                "Sente que a AISCO promove uma mentalidade de realização no trabalho, em que as ideias são encorajadas e as conquistas são valorizadas?",
            ),
            (
                "long_term_vision",
                "Do you believe that AISCO helps you to develop a long-term vision and fight for your future, rather than focusing only on the present?",
                "Acredita que a AISCO o(a) ajuda a desenvolver uma visão de longo prazo e a lutar pelo seu futuro, em vez de se focar apenas no presente?",
            ),
            (
                "supervisor_development",
                "My supervisor has been a vehicle for my professional development.",
                "O meu supervisor tem sido um veículo para o meu desenvolvimento profissional.",
            ),
        ],
    )
    add(qid, q)

    qid, q = matrix(
        "safety_wellbeing",
        "Safety and well-being at AISCO",
        "Segurança e bem-estar na AISCO",
        [
            (
                "feel_safe",
                "Do you feel safe and secure in your work environment at AISCO?",
                "Sente-se seguro(a) no seu ambiente de trabalho na AISCO?",
            ),
            (
                "wellbeing_concern",
                "Does AISCO show concern for your overall well-being (physical and mental) in the future, rather than focusing only on the present?",
                "A AISCO demonstra preocupação com o seu bem-estar geral (físico e mental) no futuro, em vez de se focar apenas no presente?",
            ),
        ],
    )
    add(qid, q)

    qid, q = matrix(
        "wage_policies",
        "Wage policies and incentives",
        "Políticas salariais e incentivos",
        [
            (
                "current_salary",
                "I am satisfied with my current salary.",
                "Estou satisfeito(a) com o meu salário atual.",
            ),
            (
                "salary_policy_improve",
                "I feel that the salary policy could be improved.",
                "Sinto que a política salarial poderia ser melhorada.",
            ),
            (
                "skills_remuneration",
                "I feel that my contribution to the company could be remunerated according to my skills.",
                "Sinto que a minha contribuição para a empresa poderia ser remunerada de acordo com as minhas competências.",
            ),
        ],
    )
    add(qid, q)

    qid, q = text_q(
        "additional_comments",
        "LONG_TEXT",
        "Do you have any additional comments about your job satisfaction at AISCO, suggestions for improvements, or ideas you'd like to share, inspired by improvements to serve you better?",
        "Tem comentários adicionais sobre a sua satisfação no trabalho na AISCO, sugestões de melhoria ou ideias que gostaria de partilhar, inspiradas em melhorias para o servir melhor?",
        False,
    )
    add(qid, q)

    return {
        "meta": {
            "defaultLocale": "en",
            "contentLocales": ["en", "pt"],
            "issuingBodyTitle": "HUMAN RESOURCES DEPARTMENT",
            "introductionHtml": INTRO_EN,
            "i18n": {
                "pt": {
                    "issuingBodyTitle": "DEPARTAMENTO DE RECURSOS HUMANOS",
                    "introductionHtml": INTRO_PT,
                }
            },
        },
        "pages": [{"id": uid(), "title": "Survey", "components": components}],
        "questions": questions,
        "rules": [],
    }


if __name__ == "__main__":
    print(json.dumps(build(), ensure_ascii=False))

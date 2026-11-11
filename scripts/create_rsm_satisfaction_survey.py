#!/usr/bin/env python3
"""Create, publish, and open the RSM employee satisfaction survey.

Every statement is a full sentence on one scale: 1 Strongly disagree through
5 Strongly agree. Agreeing is the favorable answer, except the row about
training being useful for jobs outside Raval, which stays a plain fact.

Actor: rsm.admin. Password from RSM_ADMIN_PASSWORD, otherwise the segment-swap
seed (rsm.admin → admin.rsm).

The run is identified and open to the whole company, so employees fill it
after they sign in to the Forms portal. There is no public link.
"""

from __future__ import annotations

import json
import os
import sys
import uuid

import requests

IAM = os.environ.get("IAM_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
FORMS = os.environ.get("FORMS_BASE_URL", "http://127.0.0.1:8094").rstrip("/")
USERNAME = os.environ.get("RSM_ADMIN_USERNAME", "rsm.admin")
COMPANY_ID = "5aa7230d-fe4d-4f5f-a70a-1e4b19af5a65"
FORM_NAME = "Employee satisfaction survey (RSM)"

SCALE = [
    ("1", "Strongly disagree", "በጣም አልስማማም", "Baay'ee walii hin galu"),
    ("2", "Disagree", "አልስማማም", "Walii hin galu"),
    ("3", "Average", "መካከለኛ", "Giddugaleessa"),
    ("4", "Agree", "እስማማለሁ", "Walii gala"),
    ("5", "Strongly agree", "በጣም እስማማለሁ", "Baay'ee walii gala"),
]

# (key, en, am, om)
SECTIONS: list[tuple[str, str, str, str, list[tuple[str, str, str, str]]]] = [
    (
        "job_role",
        "Job role and work environment",
        "የስራ ሚና እና የስራ አካባቢ",
        "Gahee hojii fi naannoo hojii",
        [
            (
                "tools",
                "I have the tools and resources I need to do my job effectively.",
                "ስራዬን በብቃት ለመስራት የሚያስፈልጉኝ መሣሪያዎች እና ግብዓቶች አሉኝ።",
                "Meeshaa fi qabeenya hojii koo bu'a qabeessa ta'ee hojjechuuf na barbaachisu qaba.",
            ),
            (
                "workspace",
                "My physical and digital workspace is safe and conducive to productivity.",
                "አካላዊም ዲጂታልም የስራ ቦታዬ ለምርታማነት ምቹ እና ደህንነቱ የተጠበቀ ነው።",
                "Bakki hojii koo qaamaa fi dijitaalaa nageenya qaba, oomishaafis mijataa dha.",
            ),
            (
                "workload",
                "My current workload is manageable and allows for a healthy work-life balance.",
                "አሁን ያለብኝ የስራ ጫና የሚቻል ነው፣ ጤናማ የስራና የህይወት ሚዛንም ይፈቅዳል።",
                "Ba'aan hojii amma qabu danda'amuu danda'a, madaallii fayyaa qabeessa hojii fi jireenyaas ni hayyama.",
            ),
            (
                "goals",
                "I understand how my work contributes to the organization's overall goals.",
                "ስራዬ ለድርጅቱ አጠቃላይ ግቦች እንዴት አስተዋጽኦ እንደሚያደርግ ተረድቻለሁ።",
                "Hojii koon galma waliigalaa dhaabbatichaa akkamitti gargaaru akka ta'e nan hubadha.",
            ),
        ],
    ),
    (
        "leadership",
        "Management and leadership",
        "አመራር እና አስተዳደር",
        "Hoggansa fi bulchiinsa",
        [
            (
                "clear_goals",
                "My supervisor provides clear goals and expectations.",
                "ኃላፊዬ ግቦችን እና የሚጠበቅብኝን በግልጽ ያስረዳል።",
                "Hoggansaan koo galma fi waan na irraa eegamu ifa ta'ee naaf ibsa.",
            ),
            (
                "feedback",
                "I receive regular and constructive feedback on my performance.",
                "ስለ አፈጻጸሜ ወቅታዊ እና ገንቢ አስተያየት አገኛለሁ።",
                "Gahumsa koo irratti yaada yeroo isaati fi ijaarsaa nan argadha.",
            ),
            (
                "updates",
                "My supervisor keeps me informed about important production updates and changes.",
                "ኃላፊዬ ስለ አስፈላጊ የምርት ዝመናዎች እና ለውጦች ያሳውቀኛል።",
                "Hoggansaan koo waa'ee fooyya'iinsa oomishaa fi jijjiirama barbaachisaa na beeksisa.",
            ),
            (
                "information",
                "My supervisor provides me with sufficient information related to my work.",
                "ኃላፊዬ ከስራዬ ጋር የተያያዘ በቂ መረጃ ይሰጠኛል።",
                "Hoggansaan koo odeeffannoo hojii kootti dhimmu gahaa naaf kenna.",
            ),
            (
                "listens",
                "My supervisor takes time to listen to me.",
                "ኃላፊዬ ሊያዳምጠኝ ጊዜ ይወስዳል።",
                "Hoggansaan koo na dhaggeeffachuuf yeroo fudhata.",
            ),
            (
                "career_care",
                "My supervisor cares for my career advancement.",
                "ኃላፊዬ ለሙያዬ እድገት ግድ ይለዋል።",
                "Hoggansaan koo guddina ogummaa koof ni yaada.",
            ),
            (
                "supervisor_training",
                "My supervisor offers me training opportunities.",
                "ኃላፊዬ የስልጠና እድሎችን ያቀርብልኛል።",
                "Hoggansaan koo carraa leenjii naaf dhiheessa.",
            ),
            (
                "speak_up",
                "I feel comfortable sharing my opinions and ideas with my supervisor.",
                "ሀሳቤን እና አስተያየቴን ለኃላፊዬ በነፃነት ማጋራት እችላለሁ።",
                "Yaada fi ilaalcha koo hoggansaa kootti bilisaan qooduun naaf mijata.",
            ),
        ],
    ),
    (
        "growth",
        "Growth and development",
        "እድገት እና ልማት",
        "Guddina",
        [
            (
                "career_growth",
                "I see opportunities for career growth within this organization.",
                "በዚህ ድርጅት ውስጥ የሙያ እድገት እድሎች አሉኝ።",
                "Dhaabbata kana keessatti carraan guddina ogummaa jira jedhee nan arga.",
            ),
            (
                "skill_training",
                "My supervisor provides adequate training to help me improve my skills.",
                "ሥራ አስኪያጄ ክህሎቴን ለማሻሻል በቂ ስልጠና ይሰጠኛል።",
                "Hoggansaan koo dandeettii koo fooyyessuuf leenjii gahaa naaf kenna.",
            ),
            (
                "stay",
                "I plan to continue working and grow in this factory.",
                "በዚህ ፋብሪካ መስራቴን ለመቀጠል እና ለማደግ አቅጃለሁ።",
                "Warshaa kana keessatti hojjechuu itti fufuun guddachuuf karoora qaba.",
            ),
        ],
    ),
    (
        "pay",
        "Compensation and benefits",
        "ክፍያ እና ጥቅማጥቅም",
        "Mindaa fi faayidaa",
        [
            (
                "recognized",
                "My hard work and achievements are recognized and rewarded.",
                "ጠንክሮ መስራቴ እና ውጤቶቼ እውቅና እና ሽልማት ያገኛሉ።",
                "Hojii cimaa fi milkaa'ina koo beekamtii fi badhaasa argata.",
            ),
            (
                "salary",
                "I am satisfied with my salary.",
                "በደመወዜ ረክቻለሁ።",
                "Mindaa kootti nan gammada.",
            ),
            (
                "incentives",
                "I am satisfied with the incentives (production, PPE, IR, and housekeeping).",
                "በማበረታቻዎቹ (ምርት፣ PPE፣ IR፣ እና የቤት አያያዝ) ረክቻለሁ።",
                "Kaka'umsa (oomisha, PPE, IR, fi eegumsa mana) irratti nan gammada.",
            ),
            (
                "increment",
                "I am satisfied with the yearly increment.",
                "በዓመታዊ የደመወዝ ጭማሪ ረክቻለሁ።",
                "Dabala mindaa waggaa irratti nan gammada.",
            ),
            (
                "production_incentives",
                "Production incentives motivate me to perform better.",
                "የምርት ማበረታቻዎች የተሻለ እንድሰራ ያነሳሱኛል።",
                "Kaka'umsi oomishaa caalaatti hojjechuuf na kakaasa.",
            ),
            (
                "achievable",
                "Incentives are linked to achievable targets.",
                "ማበረታቻዎች ሊደረሱ ከሚችሉ ግቦች ጋር የተያያዙ ናቸው።",
                "Kaka'umsi galma danda'amuun walqabateera.",
            ),
        ],
    ),
    (
        "responsibility",
        "Responsibility",
        "ኃላፊነት",
        "Itti gaafatamummaa",
        [
            (
                "working_time",
                "Employees here use their working time well.",
                "እዚህ ያሉ ሰራተኞች የስራ ጊዜያቸውን በአግባቡ ይጠቀማሉ።",
                "Hojjettoonni as jiran yeroo hojii isaanii haala gaariin fayyadamu.",
            ),
            (
                "errors",
                "People take responsibility for work errors.",
                "ሰዎች ለስራ ስህተቶች ኃላፊነት ይወስዳሉ።",
                "Namoonni dogoggora hojiif itti gaafatamummaa fudhatu.",
            ),
            (
                "clients",
                "People take responsibility toward clients and reporting units.",
                "ሰዎች ለደንበኞች እና ለሪፖርት ክፍሎች ኃላፊነት ይወስዳሉ።",
                "Namoonni maamiltootaa fi kutaalee itti gabaasanitti itti gaafatamummaa fudhatu.",
            ),
            (
                "supervisors",
                "Immediate supervisors take responsibility for their work.",
                "የቅርብ ኃላፊዎች ለስራቸው ኃላፊነት ይወስዳሉ።",
                "Hoggansonni kallattii hojii isaaniif itti gaafatamummaa fudhatu.",
            ),
            (
                "whole",
                "People across Raval take responsibility for their work.",
                "በራቫል ውስጥ ያሉ ሰዎች ለስራቸው ኃላፊነት ይወስዳሉ።",
                "Namoonni Raval keessa jiran hojii isaaniif itti gaafatamummaa fudhatu.",
            ),
        ],
    ),
    (
        "trust",
        "Trust",
        "እምነት",
        "Amantaa",
        [
            (
                "trust_incharge",
                "I trust my shift incharge.",
                "የፈረቃ ኃላፊዬን አምናለሁ።",
                "Hoggansaa sa'aatii hojii koo nan amana.",
            ),
            (
                "trust_hod",
                "I trust my shift HOD.",
                "የፈረቃ ክፍል ኃላፊዬን አምናለሁ።",
                "Hoggansaa kutaa sa'aatii hojii koo nan amana.",
            ),
            (
                "incharge_growth",
                "My shift incharge supports my growth.",
                "የፈረቃ ኃላፊዬ እድገቴን ይደግፋል።",
                "Hoggansaan sa'aatii hojii koo guddina koo ni deeggara.",
            ),
        ],
    ),
    (
        "relationships",
        "Relationships between employees",
        "በሰራተኞች መካከል ያለ ግንኙነት",
        "Walitti dhufeenya hojjettootaa",
        [
            (
                "other_skills",
                "People with other skills are involved in my work when needed.",
                "ሌላ ክህሎት ያላቸው ሰዎች አስፈላጊ ሲሆን በስራዬ ይሳተፋሉ።",
                "Namoonni dandeettii biraa qaban yeroo barbaachisutti hojii koo keessatti ni hirmaatu.",
            ),
            (
                "respect",
                "My coworkers treat me with respect.",
                "ባልደረቦቼ በክብር ይይዙኛል።",
                "Hojjettoonni koo na kabajuun na ilaalu.",
            ),
            (
                "help",
                "My coworkers help me when I need it.",
                "ባልደረቦቼ ስፈልግ ይረዱኛል።",
                "Hojjettoonni koo yeroon barbaadu na gargaaru.",
            ),
            (
                "team_raval",
                "There is good teamwork in Raval.",
                "በራቫል ጥሩ የቡድን ስራ አለ።",
                "Raval keessatti hojii garee gaariin jira.",
            ),
            (
                "team_department",
                "There is good teamwork in my department.",
                "በክፍሌ ጥሩ የቡድን ስራ አለ።",
                "Kutaa koo keessatti hojii garee gaariin jira.",
            ),
            (
                "relationships",
                "Relationships between employees are good.",
                "በሰራተኞች መካከል ያለው ግንኙነት ጥሩ ነው።",
                "Walitti dhufeenyi hojjettootaa gidduu jiru gaarii dha.",
            ),
        ],
    ),
    (
        "image",
        "Image of Raval",
        "የራቫል ገጽታ",
        "Bifa Raval",
        [
            (
                "company_image",
                "The company has a good image.",
                "ኩባንያው ጥሩ ገጽታ አለው።",
                "Dhaabbatichi bifa gaarii qaba.",
            ),
            (
                "public_opinion",
                "The public has a good opinion of the company.",
                "ህዝቡ ስለ ኩባንያው ጥሩ አስተያየት አለው።",
                "Ummanni dhaabbata kana irratti ilaalcha gaarii qaba.",
            ),
            (
                "image_activities",
                "Raval's activities improve the company's image.",
                "የራቫል ተግባራት የኩባንያውን ገጽታ ያሻሽላሉ።",
                "Gochaaleen Raval bifa dhaabbataa ni fooyyessu.",
            ),
            (
                "recommend",
                "I would recommend this company as a good place to work.",
                "ይህን ኩባንያ እንደ ጥሩ የስራ ቦታ እመክራለሁ።",
                "Dhaabbata kana bakka hojii gaarii ta'ee nan gorsa.",
            ),
            (
                "image_whole",
                "Raval as a whole has a good image.",
                "ራቫል በአጠቃላይ ጥሩ ገጽታ አለው።",
                "Raval waliigalaan bifa gaarii qaba.",
            ),
        ],
    ),
    (
        "training",
        "Training and opportunities",
        "ስልጠና እና እድሎች",
        "Leenjii fi carraa",
        [
            (
                "training_satisfied",
                "I am satisfied with the training opportunities in the company.",
                "በኩባንያው ውስጥ ባሉ የስልጠና እድሎች ረክቻለሁ።",
                "Carraa leenjii dhaabbata kana keessa jiru irratti nan gammada.",
            ),
            (
                "training_attended",
                "I have had the opportunity to take part in training in this company.",
                "በዚህ ኩባንያ ውስጥ በስልጠና የመሳተፍ እድል አግኝቻለሁ።",
                "Dhaabbata kana keessatti leenjii irratti hirmaachuuf carraa argeera.",
            ),
            (
                "training_career",
                "Training helped me advance in my career.",
                "ስልጠና በሙያዬ እንድገፋ ረድቶኛል።",
                "Leenjiin ogummaa koo irratti akka guddinu na gargaare.",
            ),
            (
                "training_outside",
                "Training offers me possibilities to apply for jobs outside Raval.",
                "ስልጠና ከራቫል ውጭ ለስራ የማመልከት እድል ይሰጠኛል።",
                "Leenjiin Raval ala hojii iyyachuuf carraa naaf kenna.",
            ),
            (
                "training_general",
                "I am satisfied with my training opportunities in general.",
                "በአጠቃላይ በስልጠና እድሎቼ ረክቻለሁ።",
                "Waliigalaan carraa leenjii koo irratti nan gammada.",
            ),
        ],
    ),
    (
        "competence",
        "Employee competence",
        "የሰራተኛ ብቃት",
        "Dandeettii hojjetaa",
        [
            (
                "skills_at_work",
                "I have opportunities to improve my skills and knowledge while I work.",
                "በምሰራበት ጊዜ ክህሎቴን እና እውቀቴን የማሻሻል እድሎች አሉኝ።",
                "Yeroon hojjetu dandeettii fi beekumsa koo fooyyessuuf carraa qaba.",
            ),
            (
                "work_matches",
                "My work matches my skills, and I can improve my competence.",
                "ስራዬ ከክህሎቴ ጋር ይጣጣማል፣ ብቃቴንም ማሻሻል እችላለሁ።",
                "Hojii koo dandeettii koo waliin ni walsima, dandeettii koos fooyyessuu nan danda'a.",
            ),
            (
                "clients_respect",
                "Clients respect the competence of employees.",
                "ደንበኞች የሰራተኞችን ብቃት ያከብራሉ።",
                "Maamiltoonni dandeettii hojjettootaa ni kabaju.",
            ),
            (
                "public_respect",
                "The public respects the competence of employees.",
                "ህዝቡ የሰራተኞችን ብቃት ያከብራል።",
                "Ummanni dandeettii hojjettootaa ni kabaja.",
            ),
            (
                "advancement",
                "I have opportunities for career advancement.",
                "የሙያ እድገት እድሎች አሉኝ።",
                "Carraan guddina ogummaa naaf jira.",
            ),
            (
                "competence",
                "I have the competence to do my job.",
                "ስራዬን ለመስራት የሚያስችል ብቃት አለኝ።",
                "Hojii koo hojjechuuf dandeettii qaba.",
            ),
        ],
    ),
    (
        "safety",
        "Safety",
        "ደህንነት",
        "Nageenya",
        [
            (
                "safety",
                "The company provides well for employee safety.",
                "ኩባንያው ለሰራተኞች ደህንነት በአግባቡ ያቀርባል።",
                "Dhaabbatichi nageenya hojjettootaaf haala gaariin kenna.",
            ),
        ],
    ),
]


def uid() -> str:
    return str(uuid.uuid4())


def labels(am: str, om: str) -> dict:
    return {"am": {"label": am}, "om": {"label": om}}


def question(key: str, typ: str, en: str, am: str, om: str, required: bool) -> tuple[str, dict]:
    qid = uid()
    return qid, {
        "id": qid,
        "key": key,
        "type": typ,
        "label": en,
        "required": required,
        "i18n": labels(am, om),
    }


def build() -> dict:
    questions: list[dict] = []
    about: list[dict] = []
    feedback: list[dict] = []

    def add(bucket: list[dict], qid: str, q: dict) -> None:
        questions.append(q)
        bucket.append({"id": uid(), "type": "QUESTION", "questionId": qid})

    identity = [
        ("employee_name", "Employee name", "የሰራተኛ ስም", "Maqaa hojjetaa", True),
        ("employee_id", "Employee ID", "የሰራተኛ መለያ", "Koodii hojjetaa", True),
        ("department", "Employee department", "የሰራተኛ ክፍል", "Kutaa hojii", True),
        ("reporting_manager", "Reporting manager", "ሪፖርት የሚደረግለት ኃላፊ", "Hoggansa itti gabaasitan", True),
        ("email", "Email address", "ኢሜይል አድራሻ", "Teessoo imeelii", False),
    ]
    for key, en, am, om, required in identity:
        add(about, *question(key, "SHORT_TEXT", en, am, om, required))

    columns = [
        {
            "value": value,
            "label": f"{value} {en}",
            "i18n": labels(f"{value} {am}", f"{value} {om}"),
        }
        for value, en, am, om in SCALE
    ]
    for key, en, am, om, rows in SECTIONS:
        qid = uid()
        questions.append(
            {
                "id": qid,
                "key": key,
                "type": "MATRIX",
                "label": en,
                "required": True,
                "i18n": labels(am, om),
                "configuration": {
                    "rows": [
                        {"id": uid(), "key": row_key, "label": row_en, "i18n": labels(row_am, row_om)}
                        for row_key, row_en, row_am, row_om in rows
                    ],
                    "columns": columns,
                    "allowComment": False,
                },
            }
        )
        feedback.append({"id": uid(), "type": "QUESTION", "questionId": qid})

    intro_en = (
        "<p>Please complete all required details. Rate each statement: "
        "1 Strongly disagree, 2 Disagree, 3 Average, 4 Agree, 5 Strongly agree.</p>"
    )
    intro_am = (
        "<p>እባክዎ ሁሉንም አስፈላጊ ዝርዝሮች ይሙሉ። እያንዳንዱን ዓረፍተ ነገር ይመዝኑ፦ "
        "1 በጣም አልስማማም፣ 2 አልስማማም፣ 3 መካከለኛ፣ 4 እስማማለሁ፣ 5 በጣም እስማማለሁ።</p>"
    )
    intro_om = (
        "<p>Maaloo odeeffannoo barbaachisaa ta'e hunda guutaa. Himannaa tokkoon tokkoon madaalaa: "
        "1 Baay'ee walii hin galu, 2 Walii hin galu, 3 Giddugaleessa, 4 Walii gala, 5 Baay'ee walii gala.</p>"
    )
    return {
        "meta": {
            "defaultLocale": "en",
            "contentLocales": ["en", "am", "om"],
            "issuingBodyTitle": "Raval Steel Manufacturing",
            "introductionHtml": intro_en,
            "i18n": {
                "am": {"issuingBodyTitle": "Raval Steel Manufacturing", "introductionHtml": intro_am},
                "om": {"issuingBodyTitle": "Raval Steel Manufacturing", "introductionHtml": intro_om},
            },
        },
        "pages": [
            {"id": uid(), "title": "About you", "components": about},
            {"id": uid(), "title": "Feedback", "components": feedback},
        ],
        "questions": questions,
        "rules": [],
    }


def segment_swap(username: str) -> str:
    parts = username.split(".")
    if len(parts) < 2:
        return username
    return ".".join(parts[1:] + parts[:1])


def password() -> str:
    explicit = os.environ.get("RSM_ADMIN_PASSWORD")
    if explicit:
        return explicit
    return segment_swap(USERNAME)


def login_headers() -> dict:
    login = requests.post(
        f"{IAM}/api/v1/auth/login",
        json={"strategy": "USERNAME", "identifier": USERNAME, "password": password()},
        timeout=30,
    )
    if login.status_code != 200:
        detail = ""
        try:
            body = login.json()
            if isinstance(body, dict):
                detail = str(body.get("code") or body.get("error") or body.get("message") or "")[:180]
        except ValueError:
            detail = ""
        raise SystemExit(f"Could not sign in as rsm.admin ({login.status_code}) {detail}".rstrip())
    identity = login.json().get("accessToken")
    if not identity:
        raise SystemExit("IAM login did not return an access token.")
    context = requests.post(
        f"{IAM}/api/v1/auth/context",
        headers={"Authorization": f"Bearer {identity}"},
        json={
            "applicationCode": "forms",
            "companyId": COMPANY_ID,
            "serviceCode": "forms-api",
        },
        timeout=30,
    )
    if context.status_code != 200:
        raise SystemExit(f"Forms context failed ({context.status_code}): {context.text[:300]}")
    token = context.json().get("accessToken")
    if not token:
        raise SystemExit("Forms context did not return an access token.")
    return {"Authorization": f"Bearer {token}"}


def expect(response: requests.Response, what: str) -> dict:
    if response.status_code not in (200, 201):
        raise SystemExit(f"{what} failed ({response.status_code}): {response.text[:500]}")
    if not response.content:
        return {}
    return response.json()


def main() -> None:
    definition = build()
    rows = [
        row[1]
        for _key, _en, _am, _om, section_rows in SECTIONS
        for row in section_rows
    ]
    if len(rows) != 52:
        raise SystemExit(f"expected 52 statements, got {len(rows)}")
    if any(text.startswith("I have not") for text in rows):
        raise SystemExit("a reversed training sentence is still in the form")
    if "I trust my shift incharge." not in rows:
        raise SystemExit("trust row missing")
    if "Training offers me possibilities to apply for jobs outside Raval." not in rows:
        raise SystemExit("outside-Raval training row missing")

    headers = login_headers()
    existing = expect(requests.get(f"{FORMS}/api/v1/forms", headers=headers, timeout=30), "list forms")
    for form in existing:
        if form.get("name") == FORM_NAME and form.get("status") != "ARCHIVED":
            raise SystemExit(f"A form named {FORM_NAME!r} already exists ({form.get('id')}). Left it unchanged.")

    created = expect(
        requests.post(
            f"{FORMS}/api/v1/forms",
            headers=headers,
            json={
                "name": FORM_NAME,
                "description": (
                    "Raval Steel Manufacturing employee satisfaction survey in English, Amharic, and Afaan Oromoo. "
                    "Agreeing is the favorable answer on every statement except training for jobs outside Raval."
                ),
                "definitionJson": json.dumps(definition, ensure_ascii=False),
            },
            timeout=60,
        ),
        "create form",
    )
    form_id = created["id"]
    versions = expect(
        requests.get(f"{FORMS}/api/v1/forms/{form_id}/versions", headers=headers, timeout=30),
        "list versions",
    )
    draft = next(v for v in versions if v.get("status") == "DRAFT")
    published = expect(
        requests.post(
            f"{FORMS}/api/v1/forms/{form_id}/versions/{draft['id']}/publish",
            headers=headers,
            timeout=30,
        ),
        "publish",
    )
    run = expect(
        requests.post(
            f"{FORMS}/api/v1/forms/{form_id}/runs",
            headers=headers,
            json={
                "formVersionId": published["id"],
                "name": FORM_NAME,
                "respondentMode": "IDENTIFIED",
                "status": "SCHEDULED",
            },
            timeout=30,
        ),
        "create run",
    )
    expect(
        requests.put(
            f"{FORMS}/api/v1/runs/{run['id']}/audience",
            headers=headers,
            json={"rules": [{"type": "ALL_COMPANY"}]},
            timeout=30,
        ),
        "set audience",
    )
    opened = expect(
        requests.post(f"{FORMS}/api/v1/runs/{run['id']}/open", headers=headers, timeout=30),
        "open run",
    )
    saved = json.loads(
        expect(
            requests.get(
                f"{FORMS}/api/v1/forms/{form_id}/versions/{published['id']}",
                headers=headers,
                timeout=30,
            ),
            "read published version",
        )["definitionJson"]
    )
    saved_rows = sum(len(q["configuration"]["rows"]) for q in saved["questions"] if q["type"] == "MATRIX")
    print(
        json.dumps(
            {
                "formId": form_id,
                "versionId": published["id"],
                "versionStatus": published.get("status"),
                "runId": opened["id"],
                "runStatus": opened.get("status"),
                "respondentMode": opened.get("respondentMode"),
                "questions": len(saved["questions"]),
                "statements": saved_rows,
                "locales": saved["meta"]["contentLocales"],
                "audience": "ALL_COMPANY",
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    try:
        main()
    except requests.RequestException as exc:
        print(f"request failed: {exc}", file=sys.stderr)
        sys.exit(1)

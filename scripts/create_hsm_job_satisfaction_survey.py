#!/usr/bin/env python3
"""Create, publish, and open the HSM job satisfaction survey.

Actor: hsm.admin (Forms ADMIN for Habesha Steel Mills). Password from
HSM_ADMIN_PASSWORD, otherwise the documented segment-swap seed (hsm.admin → admin.hsm).

Does not grant roles. Department and sub-department option labels stay the
official English catalog names in every language.
"""

from __future__ import annotations

import json
import os
import sys
import uuid

import requests

IAM = os.environ.get("IAM_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
FORMS = os.environ.get("FORMS_BASE_URL", "http://127.0.0.1:8094").rstrip("/")
USERNAME = os.environ.get("HSM_ADMIN_USERNAME", "hsm.admin")
COMPANY_ID = "bbc66e7e-3754-4268-82b8-68338e546cdd"
FORM_NAME = "HSM job satisfaction survey"

DEPTS: list[tuple[str, list[str]]] = [
    ("Billetyard", ["Billetyard"]),
    ("Canteen", ["Canteen"]),
    ("Electrical & Automation", ["Electrical"]),
    ("Finance & Accounts", ["Accounts and Finance"]),
    ("Gas Plant", ["Gas Plant"]),
    (
        "General Services",
        [
            "Clinic",
            "Compound Beautification",
            "General Service",
            "HO-Admin",
            "Logistics and Supply Chain",
            "Office of MD",
            "Sales and Marketing",
            "Security",
            "Transport",
        ],
    ),
    ("HR & Admin", ["HO-Admin", "HR", "Office of MD"]),
    ("IT and Innovation", ["IT"]),
    ("Leadership", ["Management"]),
    ("Maintenance-SMS", ["Garage", "Maintenance"]),
    ("Mechanical", ["Mechanical"]),
    ("ODCEO", ["ODCEO"]),
    ("Office of MD", []),
    ("Procurement", ["Logistics and Supply Chain", "Office of GM"]),
    ("Production - Rolling Mill", ["Production"]),
    (
        "Production - SMS",
        ["CCM", "Furnace", "Patching", "Scrap Grading", "Scrap Yard", "SMS"],
    ),
    ("QMS", ["QMS"]),
    ("Quality", ["Office of OD", "QC-RM", "QC-SMS"]),
    ("RM-Electrical", ["Electrical & Automation"]),
    ("RM-Mechanical", ["Mechanical"]),
    ("Sales and Marketing", ["Sales and Marketing"]),
    ("Stores", ["Finished Goods Store", "Store"]),
    ("Workshop", ["Workshop"]),
]

STATEMENTS = [
    (
        "tools",
        "I have the tools, equipment, and resources required to perform my job effectively.",
        "ስራዬን በብቃት ለመስራት የሚያስፈልጉኝ መሣሪያዎች፣ እቃዎች እና ግብዓቶች አሉኝ።",
        "Meeshaa, mi'a, fi qabeenya hojii koo bu'a qabeessa ta'ee hojjechuuf na barbaachisu qaba.",
    ),
    (
        "responsibilities",
        "I clearly understand my job responsibilities and how my work contributes to the company's goals.",
        "የስራ ኃላፊነቶቼን እና ስራዬ ለኩባንያው ግቦች እንዴት አስተዋጽኦ እንደሚያደርግ በግልጽ ተረድቻለሁ።",
        "Itti gaafatamummaa hojii koo fi hojii koon galma dhaabbataa akkamitti gargaaru akka ta'e ifa ta'ee nan hubadha.",
    ),
    (
        "supervisor",
        "My immediate supervisor communicates expectations clearly and treats employees fairly.",
        "የቅርብ ኃላፊዬ የሚጠበቅብኝን በግልጽ ያስረዳል፣ ሰራተኞችንም በፍትሕ ይይዛል።",
        "Hoggansaan koo kallattii ta'e waan na irraa eegamu ifa ta'ee ibsa, hojjettootas haqa qabeessa ta'ee ilaala.",
    ),
    (
        "feedback",
        "I receive timely feedback and support from my supervisor to improve my performance.",
        "አፈጻጸሜን ለማሻሻል ከኃላፊዬ ወቅታዊ አስተያየት እና ድጋፍ አገኛለሁ።",
        "Gahumsa koo fooyyessuuf yaada yeroo isaati fi deeggarsa hoggansaa koo irraa nan argadha.",
    ),
    (
        "respected",
        "I feel respected and valued by my colleagues and management.",
        "በባልደረቦቼም በአመራሩም የተከበርኩ እና ዋጋ የተሰጠኝ ይሰማኛል።",
        "Hojjettoonni koo fi hoggansi na kabajuu fi gatii naaf kennuu isaanii natti dhagahama.",
    ),
    (
        "teamwork",
        "There is good teamwork and cooperation within my department.",
        "በክፍሌ ውስጥ ጥሩ የቡድን ስራ እና ትብብር አለ።",
        "Kutaa koo keessatti hojii garee gaarii fi waliin hojjechuun jira.",
    ),
    (
        "safety",
        "I feel safe while working and the company gives adequate importance to health and safety.",
        "ስሰራ ደህንነቴ የተጠበቀ ይሰማኛል፣ ኩባንያውም ለጤና እና ለደህንነት በቂ ትኩረት ይሰጣል።",
        "Yeroon hojjetu nageenyin koo eegamee natti dhagahama, dhaabbatichi fayyaa fi nageenyaaf dursi gahaa kenna.",
    ),
    (
        "training",
        "The company provides adequate training and opportunities to develop my skills.",
        "ኩባንያው ክህሎቴን ለማዳበር በቂ ስልጠና እና እድሎችን ይሰጣል።",
        "Dhaabbatichi dandeettii koo guddisuuf leenjii gahaa fi carraa kenna.",
    ),
    (
        "career",
        "I believe there are fair opportunities for career growth and promotion in this company.",
        "በዚህ ኩባንያ ለሙያ ዕድገት እና ለደረጃ ማሻሻያ ፍትሐዊ እድሎች እንዳሉ አምናለሁ።",
        "Dhaabbata kana keessatti guddina ogummaa fi sadarkaa ol ka'iinsaaf carraan haqa qabeessa jira jedhee amana.",
    ),
    (
        "salary",
        "My salary and benefits are fair compared to my work and responsibilities.",
        "ደመወዜ እና ጥቅሞቼ ከስራዬ እና ከኃላፊነቴ ጋር ሲነጻጸሩ ፍትሐዊ ናቸው።",
        "Mindhaan koo fi faayidaan koo hojii fi itti gaafatamummaa koo waliin yoo madaalaman haqa qabeessa dha.",
    ),
    (
        "incentives",
        "The company's incentive and reward system motivates me to perform better.",
        "የኩባንያው የማበረታቻ እና የሽልማት ሥርዓት የተሻለ እንድሰራ ያነሳሳኛል።",
        "Sirni kaka'umsaa fi badhaasa dhaabbataa kanaa caalaatti hojjechuuf na kakaasa.",
    ),
    (
        "listens",
        "Management listens to employee suggestions and takes appropriate action whenever possible.",
        "አመራሩ የሰራተኞችን አስተያየት ያዳምጣል፣ በተቻለም ጊዜ ተገቢ እርምጃ ይወስዳል።",
        "Hoggansi yaada hojjettootaa dhaggeeffata, yeroo danda'ames tarkaanfii mijataa fudhata.",
    ),
    (
        "pride",
        "I feel proud to work for this organization and would recommend it as a good place to work.",
        "ለዚህ ድርጅት በመስራቴ ኩራት ይሰማኛል፣ እንደ ጥሩ የስራ ቦታም እመክራለሁ።",
        "Dhaabbata kanaaf hojjechuun kootti boona, bakka hojii gaarii ta'ee namootaafis nan gorsa.",
    ),
    (
        "overall",
        "Overall, I am satisfied with my job in this company.",
        "በአጠቃላይ በዚህ ኩባንያ ባለኝ ስራ ረክቻለሁ።",
        "Waliigalaan hojii dhaabbata kana keessatti qabu irratti nan gammada.",
    ),
    (
        "stay",
        "I intend to continue working with this organization for the next two years.",
        "በቀጣዮቹ ሁለት ዓመታት ከዚህ ድርጅት ጋር መስራቴን ለመቀጠል አስባለሁ።",
        "Waggoota lama dhufan keessatti dhaabbata kanaan hojjechuu itti fufuuf yaada qaba.",
    ),
]

OPENS = [
    (
        "like_most",
        "What is the one thing you like most about working in this company?",
        "በዚህ ኩባንያ ውስጥ ስለመስራት በጣም የሚወዱት አንድ ነገር ምንድን ነው?",
        "Waa'ee dhaabbata kana keessatti hojjechuu keessatti waan ati baay'ee jaallattu tokko maali?",
    ),
    (
        "improve_now",
        "What is the one thing management should improve immediately?",
        "አመራሩ ወዲያውኑ ማሻሻል ያለበት አንድ ነገር ምንድን ነው?",
        "Waan hoggansi hatattamaan fooyyessuu qabu tokko maali?",
    ),
    (
        "suggestions",
        "Do you have any suggestions to make this a better workplace?",
        "ይህንን የተሻለ የስራ ቦታ ለማድረግ አስተያየት አለዎት?",
        "Bakka hojii kana caalaatti fooyyessuuf yaada qabdaa?",
    ),
]

SCALE = [
    ("1", "Strongly disagree", "በጣም አልስማማም", "Baay'ee walii hin galu"),
    ("2", "Disagree", "አልስማማም", "Walii hin galu"),
    ("3", "Average", "መካከለኛ", "Giddugaleessa"),
    ("4", "Agree", "እስማማለሁ", "Walii gala"),
    ("5", "Strongly agree", "በጣም እስማማለሁ", "Baay'ee walii gala"),
]


def uid() -> str:
    return str(uuid.uuid4())


def labels(am: str, om: str) -> dict:
    return {"am": {"label": am}, "om": {"label": om}}


def option(value: str, en: str, am: str | None = None, om: str | None = None) -> dict:
    item = {"value": value, "label": en}
    if am and om:
        item["i18n"] = labels(am, om)
    return item


def question(
    key: str,
    typ: str,
    en: str,
    am: str,
    om: str,
    required: bool,
    configuration: dict | None = None,
    description: tuple[str, str, str] | None = None,
) -> tuple[str, dict]:
    qid = uid()
    body: dict = {
        "id": qid,
        "key": key,
        "type": typ,
        "label": en,
        "required": required,
        "i18n": labels(am, om),
    }
    if description is not None:
        body["description"] = description[0]
        body["i18n"]["am"]["description"] = description[1]
        body["i18n"]["om"]["description"] = description[2]
    if configuration is not None:
        body["configuration"] = configuration
    return qid, body


def sub_label(dept: str, sub: str) -> str:
    owners = [name for name, subs in DEPTS if sub in subs]
    return f"{sub} ({dept})" if len(owners) > 1 else sub


def build() -> dict:
    questions: list[dict] = []
    about: list[dict] = []
    agreement: list[dict] = []
    words: list[dict] = []

    def add(bucket: list[dict], qid: str, q: dict) -> None:
        questions.append(q)
        bucket.append({"id": uid(), "type": "QUESTION", "questionId": qid})

    identity = [
        ("employee_name", "Employee name", "የሰራተኛ ስም", "Maqaa hojjetaa"),
        ("employee_id", "Employee ID/Code", "የሰራተኛ መለያ / ኮድ", "Koodii hojjetaa"),
        ("designation", "Employee designation", "የስራ መደብ", "Gita hojii"),
    ]
    for key, en, am, om in identity:
        add(about, *question(key, "SHORT_TEXT", en, am, om, True))

    add(
        about,
        *question(
            "department",
            "DROPDOWN",
            "Employee department",
            "ክፍል",
            "Kutaa hojii",
            True,
            {
                "options": [
                    option(name, name) for name, _subs in DEPTS
                ]
            },
        ),
    )
    add(
        about,
        *question(
            "sub_department",
            "DROPDOWN",
            "Employee sub-department",
            "ንዑስ ክፍል",
            "Kutaa xixiqqaa",
            False,
            {
                "options": [
                    option(f"{dept} / {sub}", sub_label(dept, sub))
                    for dept, subs in DEPTS
                    for sub in subs
                ]
            },
            (
                "Leave blank if none",
                "ከሌለ ባዶ ይተው",
                "Yoo hin jirre duwwaa dhiisi",
            ),
        ),
    )
    add(
        about,
        *question(
            "site",
            "SINGLE_CHOICE",
            "Site",
            "የስራ ቦታ",
            "Bakka hojii",
            True,
            {
                "options": [
                    option("dukem", "Dukem", "ዱከም", "Dukam"),
                    option("addis_ababa", "Addis Ababa", "አዲስ አበባ", "Finfinnee"),
                ]
            },
        ),
    )
    add(about, *question("shift", "SHORT_TEXT", "Shift", "ፈረቃ", "Sa'aatii hojii", True))
    add(
        about,
        *question(
            "reporting_manager",
            "SHORT_TEXT",
            "Reporting manager",
            "ሪፖርት የሚደረግለት ኃላፊ",
            "Hoggansa itti gabaasitan",
            True,
        ),
    )
    add(
        about,
        *question(
            "gender",
            "SINGLE_CHOICE",
            "Gender",
            "ጾታ",
            "Saala",
            True,
            {
                "options": [
                    option("female", "Female", "ሴት", "Dubartii"),
                    option("male", "Male", "ወንድ", "Dhiira"),
                    option("prefer_not", "Prefer not to say", "መግለጽ አልፈልግም", "Ibsuu hin barbaadu"),
                ]
            },
        ),
    )
    add(
        about,
        *question(
            "years_in_service",
            "NUMBER",
            "Years in service",
            "የአገልግሎት ዓመታት",
            "Waggoota tajaajilaa",
            True,
        ),
    )
    add(
        about,
        *question(
            "email",
            "SHORT_TEXT",
            "Email address",
            "ኢሜይል አድራሻ",
            "Teessoo imeelii",
            False,
        ),
    )

    matrix_id = uid()
    questions.append(
        {
            "id": matrix_id,
            "key": "agreement",
            "type": "MATRIX",
            "label": "Agreement",
            "required": True,
            "i18n": labels("ስምምነት", "Walii galtee"),
            "configuration": {
                "rows": [
                    {
                        "id": uid(),
                        "key": key,
                        "label": en,
                        "i18n": labels(am, om),
                    }
                    for key, en, am, om in STATEMENTS
                ],
                "columns": [
                    {
                        "value": value,
                        "label": f"{value} {en}",
                        "i18n": labels(f"{value} {am}", f"{value} {om}"),
                    }
                    for value, en, am, om in SCALE
                ],
                "allowComment": False,
            },
        }
    )
    agreement.append({"id": uid(), "type": "QUESTION", "questionId": matrix_id})

    for key, en, am, om in OPENS:
        add(words, *question(key, "LONG_TEXT", en, am, om, False))

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
            "issuingBodyTitle": "Habesha Steel Mills",
            "introductionHtml": intro_en,
            "i18n": {
                "am": {
                    "issuingBodyTitle": "Habesha Steel Mills",
                    "introductionHtml": intro_am,
                },
                "om": {
                    "issuingBodyTitle": "Habesha Steel Mills",
                    "introductionHtml": intro_om,
                },
            },
        },
        "pages": [
            {"id": uid(), "title": "About you", "components": about},
            {"id": uid(), "title": "Agreement", "components": agreement},
            {"id": uid(), "title": "In your own words", "components": words},
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
    explicit = os.environ.get("HSM_ADMIN_PASSWORD")
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
        raise SystemExit(
            "Could not sign in as hsm.admin. Set HSM_ADMIN_PASSWORD to that account's password and run this again."
        )
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
    by_key = {q["key"]: q for q in definition["questions"]}
    if len(definition["questions"]) != 15:
        raise SystemExit(f"expected 15 questions, got {len(definition['questions'])}")
    if len(by_key["department"]["configuration"]["options"]) != 23:
        raise SystemExit("department list is not the 23-name catalog")
    if len(by_key["sub_department"]["configuration"]["options"]) != 42:
        raise SystemExit("sub-department list changed")
    if by_key["shift"]["type"] != "SHORT_TEXT" or not by_key["shift"]["required"]:
        raise SystemExit("shift must stay required free text")
    if [row["key"] for row in by_key["agreement"]["configuration"]["rows"]] != [s[0] for s in STATEMENTS]:
        raise SystemExit("agreement rows drifted")

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
                    "Habesha Steel Mills job satisfaction survey in English, Amharic, and Afaan Oromoo. "
                    "Department names stay the official roster names."
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
                "locales": saved["meta"]["contentLocales"],
                "shift": next(q["type"] for q in saved["questions"] if q["key"] == "shift"),
                "agreementRows": len(
                    next(q for q in saved["questions"] if q["key"] == "agreement")["configuration"]["rows"]
                ),
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

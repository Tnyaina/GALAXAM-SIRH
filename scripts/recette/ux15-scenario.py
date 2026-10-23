#!/usr/bin/env python3
"""Prépare un scénario Pointage par API dans le PostgreSQL jetable UX-15."""

from __future__ import annotations

import calendar
import json
import os
import random
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo


CONTAINER = os.environ.get("UX15_PG_CONTAINER", "galaxam-ux15-pg")
DATABASE = os.environ.get("UX15_DB_NAME", "galaxam_ux15")
DB_USER = os.environ.get("UX15_DB_USER", "ux15_app")
API_BASE = os.environ.get("UX15_API_BASE", "http://127.0.0.1:18082/api").rstrip("/")
WEB_BASE = os.environ.get("UX15_WEB_BASE", "http://127.0.0.1:18090").rstrip("/")
ADMIN_EMAIL = os.environ.get("UX15_ADMIN_EMAIL", "ux15.admin@example.invalid")
ADMIN_PASSWORD = os.environ.get("UX15_ADMIN_PASSWORD")


def fail(message: str) -> None:
    raise RuntimeError(message)


def container_guard() -> None:
    if (CONTAINER, DATABASE, DB_USER) != ("galaxam-ux15-pg", "galaxam_ux15", "ux15_app"):
        fail("Refus : utiliser les identifiants du PostgreSQL jetable UX-15 uniquement.")
    try:
        raw = subprocess.check_output(["docker", "inspect", CONTAINER], text=True)
        d = json.loads(raw)[0]
    except Exception as exc:
        fail(f"Conteneur PostgreSQL de recette introuvable : {exc}")
    if d.get("Name") != "/galaxam-ux15-pg" or not d.get("Config", {}).get("Image", "").startswith("postgres:15"):
        fail("Refus : identité du conteneur PostgreSQL inattendue.")
    env = dict(item.split("=", 1) for item in d["Config"].get("Env", []) if "=" in item)
    if env.get("POSTGRES_DB") != DATABASE or env.get("POSTGRES_USER") != DB_USER:
        fail("Refus : le conteneur ne déclare pas la base et l'utilisateur UX-15 attendus.")
    mounts = d.get("Mounts", [])
    if any(m.get("Type") != "volume" or m.get("Destination") != "/var/lib/postgresql/data" for m in mounts):
        fail("Refus : montage de conteneur non jetable détecté.")
    if any("galaxam_local_pg_data" in str(m) for m in mounts):
        fail("Refus : volume PostgreSQL local détecté.")
    bindings = d.get("HostConfig", {}).get("PortBindings", {}).get("5432/tcp", [])
    if not bindings or any(x.get("HostIp") != "127.0.0.1" or x.get("HostPort") != "55434" for x in bindings):
        fail("Refus : PostgreSQL n'est pas publié uniquement sur 127.0.0.1:55434.")
    parsed = urllib.parse.urlparse(API_BASE)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"} or parsed.port != 18082:
        fail("Refus : UX15_API_BASE doit viser le backend local isolé sur le port 18082.")
    if not ADMIN_PASSWORD:
        fail("Définir UX15_ADMIN_PASSWORD avec le secret temporaire du compte synthétique.")
    db = psql("SELECT current_database()")
    if db != DATABASE:
        fail(f"Refus : le conteneur répond pour la base '{db}'.")
    employee_count = int(psql("SELECT COUNT(*) FROM employe"))
    if employee_count != 0:
        fail("La base contient des dossiers salariés canoniques. Arrêter le backend puis exécuter ux15-reset-canonical-demo-data.sh.")


def psql(sql: str) -> str:
    return subprocess.check_output(
        ["docker", "exec", CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", DB_USER, "-d", DATABASE, "-Atc", sql],
        text=True,
    ).strip()


def request(method: str, path: str, token: str | None = None, body: object | None = None):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API_BASE}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            raw = response.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        details = exc.read().decode(errors="replace")
        fail(f"{method} {path} → HTTP {exc.code}: {details}")


def previous_month(reference: date) -> tuple[date, date]:
    first_this_month = reference.replace(day=1)
    end = first_this_month - timedelta(days=1)
    return end.replace(day=1), end


def lookup(items, predicate, description):
    match = next((item for item in items if predicate(item)), None)
    if match is None:
        fail(f"Référentiel requis absent ou inactif : {description}.")
    return match


def main() -> None:
    container_guard()
    ping = request("GET", "/auth/ping")
    if ping.get("status") != "UP":
        fail("Le backend de recette n'est pas disponible.")
    auth = request("POST", "/auth/login", body={"email": ADMIN_EMAIL, "motDePasse": ADMIN_PASSWORD})
    token = auth.get("accessToken")
    if not token:
        fail("Le compte synthétique n'a pas reçu de jeton d'accès.")

    org = request("GET", "/referentiel/formulaire-employe", token)
    career = request("GET", "/ref-carriere/all", token)
    company = lookup(org["filiales"], lambda x: x.get("codeFiliale") == "GLX" and x.get("estActif") is True, "filiale GLX")
    site = lookup(org["sites"], lambda x: x.get("codeSite") == "TMM" and x.get("estActif") is True, "site TMM")
    department = lookup(org["departements"], lambda x: x.get("codeDepartement") == "LOG" and x.get("estActif") is True, "département LOG")
    service = lookup(org["services"], lambda x: x.get("codeService") == "ADM" and x.get("estActif") is True and x.get("idDepartement") == department["idDepartement"], "service ADM actif de LOG")
    function = lookup(org["fonctions"], lambda x: x.get("codeFonction") == "RESP_TOAMASINA" and x.get("estActif") is True and not x.get("dispensePointage"), "fonction non dispensée de pointage")
    employee_type = lookup(org["typesIntervenant"], lambda x: x.get("code") == "PERMANENT" and x.get("estActif") is True, "salarié permanent")
    contract_type = lookup(org["typesContrat"], lambda x: x.get("code") == "CDI" and x.get("estActif") is True, "CDI")
    category = lookup(career["categoriesPro"], lambda x: x.get("code") == "A" and x.get("actif") is True, "catégorie agent")
    pay_mode = lookup(career["modesPaie"], lambda x: x.get("code") == "MENSUEL" and x.get("actif") is True, "paie mensuelle")
    event = lookup(career["typesEvenement"], lambda x: x.get("code") == "EMBAUCHE_INITIALE", "événement embauche initiale")

    today = datetime.now(ZoneInfo("Indian/Antananarivo")).date()
    start, end = previous_month(today)
    suffix = f"{today:%y%m%d}{random.SystemRandom().randrange(1000, 9999)}"
    cin = f"{today:%y%m%d}{random.SystemRandom().randrange(100000, 999999)}"
    employee = request("POST", "/employes", token, {
        "matricule": f"UX15-{suffix}", "nom": "RECETTE", "prenom": "UX15", "sexe": "M",
        "dateNaissance": "1990-01-01", "lieuNaissance": "Toamasina", "nationalite": "Malgache",
        "cinNumero": cin, "cinDateDelivrance": "2010-01-01", "cinLieuDelivrance": "Toamasina",
        "adresse": "Donnée synthétique UX-15", "ville": "Toamasina", "telephone": "0000000000",
    })
    employee_id = employee.get("idEmploye")
    if not employee_id:
        fail("L'API n'a pas retourné l'identifiant du salarié synthétique.")
    # Canary de base : prouve que l'API Spring Boot écrit dans le même conteneur/base contrôlé ci-dessus.
    if psql(f"SELECT COUNT(*) FROM employe WHERE id_employe={int(employee_id)} AND matricule='{employee['matricule']}'") != "1":
        fail("Le salarié canari n'apparaît pas dans le PostgreSQL UX-15 : backend potentiellement mal connecté.")

    workflow = request("POST", f"/employes/{employee_id}/workflows/embauche-initiale", token, {
        "idFiliale": company["idFiliale"], "idTypeContrat": contract_type["idTypeContrat"],
        "idTypeIntervenant": employee_type["idTypeIntervenant"], "dateDebut": start.isoformat(),
        "dateSignature": start.isoformat(), "dureeEssaiMois": 0, "comptabiliseEffectif": True,
        "numeroContrat": f"UX15-CTR-{suffix}", "idSite": site["idSite"],
        "idDepartement": department["idDepartement"], "idService": service["idService"],
        "idFonction": function["idFonction"], "idCategorieProfessionnelle": category["idCategorieProfessionnelle"],
        "idModeCalculPaie": pay_mode["idModeCalculPaie"], "salaireBase": 1_200_000,
        "dateEffet": start.isoformat(), "codeTypeEvenement": event["code"], "motif": "Démonstration isolée UX-15",
    })
    wf_id = workflow.get("idWorkflow")
    validated = request("PUT", f"/workflows/{wf_id}/valider", token, {})
    if validated.get("codeStatutWorkflow") != "ACTIF":
        fail("Le workflow d'embauche n'est pas devenu actif.")

    code = f"UX15-{start:%Y%m}-{suffix[-4:]}"
    request("POST", "/periodes", token, {"code": code, "libelle": "Recette Pointage → Paie UX-15",
            "dateDebut": start.isoformat(), "dateFin": end.isoformat(), "statutCode": "BROUILLON"})
    periods = request("GET", "/periodes?lightweight=false", token)
    period = lookup(periods, lambda x: x.get("code") == code, code)
    period_id = int(period["idPeriodePaie"])
    opened = request("POST", f"/periodes/{period_id}/actions/OPEN", token, {"version": period["version"]})
    if opened.get("state") != "EN_COURS":
        fail("L'ouverture normale de la période a échoué.")

    pointages = []
    day = start
    while day <= end:
        if day.weekday() < 5:
            grid = request("GET", f"/pointages/saisie?idSite={site['idSite']}&date={day.isoformat()}&idEmploye={employee_id}", token)
            if not grid.get("isFerie"):
                row = next((item for item in grid.get("grilleData", []) if item.get("idEmploye") == employee_id), None)
                if row is None:
                    fail(f"Le salarié n'est pas éligible au Pointage le {day.isoformat()}.")
                params = grid.get("siteParams") or {}
                required = ("heureDebutMatin", "heureFinMatin", "heureDebutAm", "heureFinAm")
                if any(not params.get(name) for name in required):
                    fail("Les plages horaires du site ne sont pas entièrement configurées.")
                row.update({"codeStatut": "PRESENT", "heureEntreeMatin": params["heureDebutMatin"],
                            "heureSortieMatin": params["heureFinMatin"], "heureEntreeAm": params["heureDebutAm"],
                            "heureSortieAm": params["heureFinAm"]})
                # horaireContinu n'est volontairement pas modifié : on suit les plages du site.
                pointages.append(row)
        day += timedelta(days=1)
    if not pointages:
        fail("Aucune journée ouvrable ne peut être saisie pour cette période.")
    saved = request("POST", "/pointages/saisie/batch", token, pointages)
    if saved.get("success") is not True:
        fail("L'enregistrement normal des journées de pointage a échoué.")
    preflight = request("GET", f"/pointages/synthese/preflight?idPeriodePaie={period_id}", token)
    if preflight.get("nombreEmployes") != 1:
        fail(f"La population Pointage doit être strictement limitée au salarié synthétique (observé : {preflight.get('nombreEmployes')}).")
    if preflight.get("pret") is not True:
        fail("Le préflight bloque la transmission : " + json.dumps(preflight, ensure_ascii=False))

    current = lookup(request("GET", "/periodes?lightweight=false", token), lambda x: x.get("idPeriodePaie") == period_id, code)
    submitted = request("POST", f"/periodes/{period_id}/actions/SUBMIT", token,
                        {"version": current["version"], "idSite": site["idSite"]})
    if submitted.get("state") != "TRANSMIS_DAF":
        fail("La transmission de la période n'a pas été acceptée.")
    current = lookup(request("GET", "/periodes?lightweight=false", token), lambda x: x.get("idPeriodePaie") == period_id, code)
    approved = request("POST", f"/periodes/{period_id}/actions/APPROVE", token,
                       {"version": current["version"], "idSite": site["idSite"]})
    if approved.get("state") != "VALIDEE":
        fail("La validation et le gel du pointage n'ont pas été acceptés.")

    snapshot_count = int(psql(f"SELECT COUNT(*) FROM pointage_cumul_periode WHERE id_periode_paie={period_id} AND id_employe={employee_id} AND statut_snapshot='GELE'"))
    pointage_count = int(psql(f"SELECT COUNT(*) FROM pointage_jour WHERE id_periode_paie={period_id} AND id_employe={employee_id}"))
    if snapshot_count != 1 or pointage_count != len(pointages):
        fail(f"Preuve PostgreSQL inattendue : snapshots={snapshot_count}, jours={pointage_count}, attendus={len(pointages)}.")

    print(json.dumps({"status": "POINTAGE_GELE", "database": DATABASE, "employeeId": employee_id,
        "matricule": employee["matricule"], "periodId": period_id, "periodCode": code,
        "periodStart": start.isoformat(), "periodEnd": end.isoformat(), "workDaysSaved": len(pointages),
        "hours": "plages TMM API: 08:00–12:00 et 14:00–18:00; horaireContinu conservé comme renvoyé",
        "preflight": preflight, "periodState": approved.get("state"), "frozenSnapshotCount": snapshot_count,
        "payrollVariablesUrl": f"{WEB_BASE}/paie/periodes/{period_id}/variables",
        "next": "Ouvrir l'URL Variables dans React pour poursuivre le contrôle visuel Paie."}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"UX-15 interrompu : {error}", file=sys.stderr)
        sys.exit(1)

import json
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080/api"

def request(url, method="GET", data=None, token=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    body = json.dumps(data).encode("utf-8") if data is not None else None
    try:
        with urllib.request.urlopen(req, data=body) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            return e.code, json.loads(content)
        except:
            return e.code, content

def main():
    print("=================================================================")
    print("--- 1. AUTHENTICATION (REST API) ---")
    status, auth_resp = request(f"{BASE_URL}/auth/login", "POST", {
        "identifiant": "admin",
        "motDePasse": "Pass1234"
    })
    token = auth_resp.get("accessToken")
    print(f"Auth status: {status}, token: {token[:20]}...")

    print("\n--- 2. LIST REFERENTIAL TYPES MOUVEMENT PAIE ---")
    status, types_mvt = request(f"{BASE_URL}/referentiel/types-mouvement-paie", "GET", token=token)
    print(f"Status: {status}, Total types configured: {len(types_mvt)}")
    for t in types_mvt:
        print(f"  [{t['code']}] {t['libelle']} | Sens: {t['sens']} | Cat: {t['categorie']} | Proratisé: {t['estProratise']} | 1/10e: {t['estPlafonneDixieme']}")

    print("\n--- 3. REOPEN PERIOD 1 (2025-12) FOR AUDIT RECALCULATION ---")
    status, reopen_resp = request(f"{BASE_URL}/api/v1/paie/rouvrir-periode", "POST", {
        "idPeriodePaie": 1,
        "motif": "Intégration des variables de paie et primes réelles modèle Galaxam"
    }, token=token)
    print(f"Reopen status: {status}, resp: {reopen_resp}")

    print("\n--- 4. INSERT REAL ADJUSTMENTS ACCORDING TO MODEL SHEETS ---")
    adjustments = [
        # Raketaka (idEmploye 1, EMP002) :
        # - CALL PERSO : 21 470 Ar
        # - PRIME DE PERFORMANCE : 30 000 Ar
        {
            "idEmploye": 1,
            "idPeriodePaie": 1,
            "typeMouvement": "CALL_PERSO",
            "montant": 21470.00,
            "motif": "Dépassement forfait data/voix privé",
            "statut": "APPROUVE"
        },
        {
            "idEmploye": 1,
            "idPeriodePaie": 1,
            "typeMouvement": "PRIME_PERFORMANCE",
            "montant": 30000.00,
            "motif": "Prime de performance mensuelle",
            "statut": "APPROUVE"
        },
        # Rasoa (idEmploye 2, EMP003) :
        # - ACOMPTE 15 : 50 000 Ar
        {
            "idEmploye": 2,
            "idPeriodePaie": 1,
            "typeMouvement": "ACOMPTE_15",
            "montant": 50000.00,
            "motif": "Acompte de quinzaine du 15 décembre",
            "statut": "APPROUVE"
        },
        # Rakoto (idEmploye 3, EMP004) :
        # - RETENUE DIVERSE : 7 500 Ar
        {
            "idEmploye": 3,
            "idPeriodePaie": 1,
            "typeMouvement": "RETENUE_DIVERSE",
            "montant": 7500.00,
            "motif": "Pénalité dégradation badge / matériel",
            "statut": "APPROUVE"
        }
    ]

    for adj in adjustments:
        status, resp = request(f"{BASE_URL}/api/v1/paie/ajustements", "POST", adj, token=token)
        print(f"  POST /ajustements [{adj['typeMouvement']}] for emp {adj['idEmploye']}: status={status}, idAvance={resp.get('idAvance')}")

    print("\n--- 5. LIST REGISTERED ADJUSTMENTS FOR PERIOD 1 ---")
    status, list_adj = request(f"{BASE_URL}/api/v1/paie/ajustements?idPeriode=1", "GET", token=token)
    print(f"Status: {status}, Total adjustments: {len(list_adj)}")
    for a in list_adj:
        print(f"  Emp {a['matricule']} ({a['nomComplet']}) | {a['typeMouvement']} ({a['sens']}) : {a['montant']} Ar | Statut: {a['statut']}")

    print("\n--- 6. EXECUTE FULL PAYROLL CALCULATION (VALIDE / REEL) ---")
    status, calc_resp = request(f"{BASE_URL}/api/v1/paie/calculer-periode", "POST", {
        "idPeriodePaie": 1,
        "simulation": False
    }, token=token)
    print(f"Calc status: {status}")
    print(f"  Nb bulletins calculés : {calc_resp.get('nbBulletinsCalcules')}")
    print(f"  Masse salariale brute : {calc_resp.get('totalMasseSalarialeBrute')} Ar")
    print(f"  Total net à payer     : {calc_resp.get('totalNetAPayer')} Ar")
    print(f"  Charges patronales    : {calc_resp.get('totalChargesPatronales')} Ar")
    print(f"  Coût global employeur : {calc_resp.get('totalCoutGlobal')} Ar")

    print("\n--- 7. RETRIEVE DETAILED BULLETINS AND LINES ---")
    status, bulletins = request(f"{BASE_URL}/api/v1/paie/periode/1/bulletins", "GET", token=token)
    print(f"Found {len(bulletins)} bulletins for period 1:")

    for b in bulletins:
        b_id = b['idBulletin']
        status, full_bp = request(f"{BASE_URL}/api/v1/paie/bulletin/{b_id}", "GET", token=token)
        bp = full_bp.get('bulletin', {})
        lignes = full_bp.get('lignes', [])
        print(f"\n" + "="*70)
        print(f"BULLETIN: {bp.get('referenceBulletin')} | {bp.get('matricule')} - {bp.get('nomPrenom')}")
        print(f"Statut                 : {bp.get('statut')}")
        print(f"Salaire Base Contractuel: {bp.get('salaireBaseContractuel')} Ar")
        print(f"Total Gains Bruts      : {bp.get('totalGainsBruts')} Ar")
        print(f"Total Cotis Salariales : {bp.get('totalCotisationsSalariales')} Ar")
        print(f"Salaire Net Imposable  : {bp.get('salaireNetImposable')} Ar")
        print(f"IRSA Net               : {bp.get('impotIrsaNet')} Ar")
        print(f"Indemnités Net (Repas/Transport/Extra): {bp.get('totalIndemnitesNonImposables')} Ar")
        print(f"Retenues Trésorerie    : {bp.get('totalRetenuesAcomptePret')} Ar")
        print(f"Net Avant Arrondi      : {bp.get('netAvantArrondi')} Ar")
        print(f">>> NET À PAYER        : {bp.get('netAPayer')} Ar <<<")
        print(f"--- Ventilation des lignes ({len(lignes)}) ---")
        for l in lignes:
            sal = f"{l.get('montantSalarial'):>12} Ar" if l.get('montantSalarial') is not None else "            "
            pat = f"{l.get('montantPatronal'):>12} Ar" if l.get('montantPatronal') is not None else "            "
            print(f"  [{l.get('ordre'):03d}] {l.get('codeRubrique'):<24} | {l.get('libelleRubrique'):<38} | Sal: {sal} | Pat: {pat}")

    print("\n--- 8. VERIFY POST-CALCULATION ADJUSTMENT STATUS (IMPUTE_PAIE) ---")
    status, list_adj_post = request(f"{BASE_URL}/api/v1/paie/ajustements?idPeriode=1", "GET", token=token)
    for a in list_adj_post:
        print(f"  Emp: {a['matricule']} | {a['typeMouvement']} | Montant: {a['montant']} Ar | Statut: {a['statut']} | IdBulletin: {a['idBulletin']}")

    print("\n--- 9. CLOSE PERIOD DEFINITIVELY WITH IMMUTABILITY LOCK ---")
    status, close_resp = request(f"{BASE_URL}/api/v1/paie/cloturer-periode", "POST", {
        "idPeriodePaie": 1
    }, token=token)
    print(f"Cloture status: {status}, resp: {close_resp}")

if __name__ == "__main__":
    main()

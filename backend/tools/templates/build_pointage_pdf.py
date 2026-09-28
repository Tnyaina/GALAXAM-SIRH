"""Source du modèle PDF remplissable. Conception uniquement : les données viennent de Java."""
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import landscape,A4
from reportlab.lib.colors import HexColor,white
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/templates/pdf/MODELE_FICHE_POINTAGE.pdf'
W,H=landscape(A4); green=HexColor('#304D3F'); pale=HexColor('#EAF0EB'); border=HexColor('#CBD6CE')
c=canvas.Canvas(str(OUT),pagesize=(W,H));c.setTitle('GALAXAM - Modèle de fiche individuelle de pointage')
def txt(x,y,t,size=8,bold=False):
 c.setFont('Helvetica-Bold' if bold else 'Helvetica',size);c.setFillColor(green);c.drawString(x,y,t)
def field(name,x,y,w,h=13,size=8,multi=False):
 c.acroForm.textfield(name=name,x=x,y=y,width=w,height=h,fontName='Helvetica',fontSize=size,
  borderWidth=0,fillColor=white,textColor=green,forceBorder=False,maxlen=2000,fieldFlags='multiline' if multi else '')
def band(y,label):
 c.setFillColor(green);c.rect(30,y-4,W-60,18,fill=1,stroke=0);c.setFillColor(white);c.setFont('Helvetica-Bold',9);c.drawString(36,y+1,label)
def head(prefix,title):
 txt(30,H-38,'GALAXAM LOGISTICS',16,True);txt(550,H-32,title,10,True)
 field(prefix+'reference',550,H-50,260,12,7)
 field(prefix+'edition',550,H-64,260,12,7)
 c.setStrokeColor(green);c.line(30,H-75,W-30,H-75)
 for label,n,x,w in [('Salarié','nom',30,240),('Matricule','matricule',285,105),('Fonction / site','affectation',405,405)]:
  txt(x,H-90,label,7,True);field(prefix+n,x,H-108,w,14,9)
 field(prefix+'periode',30,H-126,520,14,8);field(prefix+'source',550,H-126,260,14,8)
def table(y,headers,widths,prefix,keys,rows,height=11):
 x=30;c.setFillColor(pale);c.rect(x,y-15,W-60,17,fill=1,stroke=0)
 for label,w in zip(headers,widths):txt(x+3,y-9,label,7,True);x+=w
 for r in range(rows):
  x=30;cy=y-17-(r+1)*height
  for k,w in zip(keys,widths):
   c.setStrokeColor(border);c.rect(x,cy,w,height,fill=0,stroke=1)
   field(f'{prefix}{r}_{k}',x+2,cy+1,w-4,height-2,6.5,multi=height>20)
   x+=w
head('j_','FICHE INDIVIDUELLE DE POINTAGE')
band(H-152,'I. JOURNAL QUOTIDIEN - QUANTITÉS D’INDEMNITÉS ACCORDÉES')
widths=[48,115,43,43,43,43,53,47,64,64,64,115]
# Sum must match the printable width.
widths[-1]=(W-60)-sum(widths[:-1])
headers=['Date','Statut','Entrée M','Sortie M','Entrée AM','Sortie AM','Heures','Retard','','','','Observation']
table(H-178,headers,widths,'j_day_',['date','statut','em','sm','ea','sa','heures','retard','q0','q1','q2','observation'],32,10.6)
x=30+sum(widths[:8])
for i in range(3):field(f'j_allowance_{i}',x+2,H-187,widths[8+i]-4,12,6.5);x+=widths[8+i]
field('j_note',30,44,W-60,26,7,True)
txt(30,27,'Les heures, quantités et montants sont fournis par le backend. Aucun calcul métier dans le PDF.',7)
c.showPage()
head('r_','BILAN ET VALIDATION DAF')
band(H-151,'II. CONSOLIDATION HEBDOMADAIRE')
widths=[125,82,82,82,82,82,82,(W-60)-617]
table(H-177,['Semaine / dates','Objectif (h)','Travaillé (h)','Retard (min)','Compensé (h)','Déficit (h)','HS 30 % (h)','HS 50 % (h)'],widths,'r_week_',['dates','objectif','travaille','retard','compense','deficit','hs30','hs50'],6,13)
band(304,'III. RAPPROCHEMENT DE FIN DE PÉRIODE')
labels=['Déficit semaines','HS30 avant','HS50 avant','Divers DAF (+/-)','Solde final','Absence finale','HS30 finales','HS50 finales']
for i,l in enumerate(labels):
 x=30+i*(W-60)/8;txt(x+3,287,l,7,True);field('r_final_'+str(i),x+3,269,(W-60)/8-6,14,9)
band(247,'IV. INDEMNITÉS - QUANTITÉS ET MONTANTS CONSOLIDÉS')
table(224,['Indemnité / périodicité','Quantité','Montant (Ar)'],[510,110,(W-60)-620],'r_allow_',['label','quantite','montant'],5,13)
band(126,'V. RÉGULARISATIONS DAF / OBSERVATIONS DU JOURNAL')
table(103,['Durée (h)','Motif','Date / auteur / statut'],[65,490,(W-60)-555],'r_adj_',['duree','motif','auteur'],2,24)
field('r_note',30,17,470,22,6.5,True)
txt(510,22,'Agent de pointage : date / visa',7,True);txt(660,22,'DAF : date / visa',7,True)
c.save()
print(OUT)

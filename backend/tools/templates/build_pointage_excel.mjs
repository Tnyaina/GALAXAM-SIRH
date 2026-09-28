import { fileURLToPath } from 'node:url';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';
const wb=Workbook.create(), s=wb.worksheets.add('Pointage individuel');
s.showGridLines=false;
s.getRange('A1:L79').format.font={name:'Calibri',size:10,color:'#263A31'};
s.getRange('A1:L79').format.columnWidth=13;
s.getRange('A1:L79').format.rowHeight=20;
s.getRange('A1:L79').format.verticalAlignment='center';
s.getRange('C7:C40').format.columnWidth=22;
s.getRange('L7:L40').format.columnWidth=30;
function val(cell,value){s.getRange(cell).values=[[value]];}
function band(row,title){s.getRange(`A${row}:L${row}`).merge();val(`A${row}`,title);s.getRange(`A${row}:L${row}`).format.fill='#304D3F';s.getRange(`A${row}:L${row}`).format.font={bold:true,color:'#FFFFFF',size:11};s.getRange(`A${row}:L${row}`).format.rowHeight=26;}
function header(row,values){s.getRange(`A${row}`).write([values]);s.getRange(`A${row}:L${row}`).format.fill='#EAF0EB';s.getRange(`A${row}:L${row}`).format.font={bold:true,color:'#304D3F'};s.getRange(`A${row}:L${row}`).format.wrapText=true;s.getRange(`A${row}:L${row}`).format.rowHeight=34;}
band(1,'GALAXAM LOGISTICS - FICHE INDIVIDUELLE DE POINTAGE');
s.getRange('A2:L2').merge();val('A2','Données calculées par le SIRH - quantités quotidiennes et montants consolidés');
header(4,['Salarié','','Matricule','','Fonction','','Site','','Catégorie','','Période','']);
for(const [range,token] of [['A5:B5','NOM'],['C5:D5','MATRICULE'],['E5:F5','FONCTION'],['G5:H5','SITE'],['I5:J5','CATEGORIE'],['K5:L5','PERIODE']]){s.getRange(range).merge();val(range.split(':')[0],`{{${token}}}`);}
s.getRange('A5:L5').format.wrapText=true;s.getRange('A5:L5').format.rowHeight=48;
header(7,['Date','Jour','Statut','Entrée matin','Sortie matin','Entrée AM','Sortie AM','Heures (déc.)','Retard (min)','{{INDEMNITE_1}} (unité)','{{INDEMNITE_2}} (unité)','Observation']);
s.getRange('A8:L39').format.borders={preset:'all',style:'thin',color:'#D7E0D9'};
s.getRange('H8:K39').setNumberFormat('0.00');val('A8','{{JOURNAL}}');
header(40,['TOTAL JOURNAL']);for(const col of ['H','I','J','K']) s.getRange(`${col}40`).formulas=[[`=SUM(${col}8:${col}39)`]];
band(42,'BILAN HEBDOMADAIRE');header(43,['Semaine','Du','Au','Objectif (h)','Travaillé (h)','Retards (min)','Compensé (h)','Déficit (h)','HS 30 % (h)','HS 50 % (h)']);val('A44','{{SEMAINES}}');s.getRange('D44:J49').setNumberFormat('0.00');
band(51,'BILAN FINAL DE PÉRIODE');header(52,['Déficit des semaines (h)','HS 30 % des semaines (h)','HS 50 % avant (h)','Ajustements DAF (h)','Solde final (h)','Absence finale (h)','HS 30 % finales (h)','HS 50 % finales (h)','Nuit (h)','Dimanche (h)','Férié travaillé (h)']);s.getRange('A52:L52').format.rowHeight=50;val('A53','{{BILAN}}');s.getRange('A53:K53').setNumberFormat('0.00');
band(55,'RÉCAPITULATIF DES INDEMNITÉS');header(56,['Code','Libellé','','','','Quantité','Montant (Ar)','Périodicité']);for(let r=56;r<=64;r++)s.getRange(`B${r}:E${r}`).merge();val('A57','{{INDEMNITES}}');s.getRange('A57:A64').format.wrapText=true;s.getRange('A57:L64').format.rowHeight=34;for(let r=56;r<=64;r++)s.getRange(`H${r}:L${r}`).merge();s.getRange('F57:F64').setNumberFormat('0.00');s.getRange('G57:G64').setNumberFormat('#,##0.00 "Ar"');
band(66,'RÉGULARISATIONS DAF');header(67,['Durée signée (h)','Motif','','','','','','','Date','Auteur','Statut']);val('A68','{{AJUSTEMENTS}}');s.getRange('A68:A73').setNumberFormat('+0.00;-0.00;0.00');
for(let r=68;r<=73;r++)s.getRange(`B${r}:H${r}`).merge();s.getRange('B68:H73').format.wrapText=true;s.getRange('A68:L73').format.rowHeight=38;s.getRange('I68:K73').format.wrapText=true;
s.getRange('A75:L75').merge();val('A75','{{PROVENANCE}}');s.getRange('A75:L75').format.wrapText=true;s.getRange('A75:L75').format.rowHeight=50;
s.getRange('A76:L76').merge();val('A76','Quantités = valeurs accordées stockées ; les forfaits sans quantité journalière sont indiqués par un tiret.');
header(78,['Agent de pointage','','','','','','Validation DAF']);val('A79','Date / visa :');val('G79','Date / visa :');s.freezePanes.freezeRows(7);
const target = fileURLToPath(new URL('../../src/main/resources/templates/mensuel/MODELE_POINTAGE_EXCEL.xlsx', import.meta.url));
await (await SpreadsheetFile.exportXlsx(wb)).save(target);
console.log(target);

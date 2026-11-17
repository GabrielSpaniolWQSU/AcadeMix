import {AfterViewInit,ChangeDetectorRef,Component,ElementRef,EventEmitter,NgZone,Output,ViewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom,timeout} from 'rxjs';
import {ApiService} from '../services/api.service';

interface Membro{cargo:string;nome:string;periodo:string}
interface Pedido{
 modelo:string;instituicao:string;cidade:string;curso:string;data:string;inicio:string;fim:string;
 local:string;assunto:string;participantes:string;discussoes:string;decisoes:string;responsavel:string;
 secretario:string;mandato:string;nomeParticipante:string;rg:string;cpf:string;dataEmissao:string;
 membros:Membro[];titulo:string;texto:string;revisado:boolean;registroId:number;
}
interface Previa{titulo:string;texto:string;assinaturas:Membro[]}

@Component({standalone:true,selector:'app-criador-documento',imports:[FormsModule],template:`
<dialog #janela class="assistente" aria-labelledby="titulo-assistente" (cancel)="cancelar($event)" (click)="clicarFundo($event)">
 <header class="topo"><div><h2 id="titulo-assistente">Criar documento</h2><p>Etapa {{etapa}} de 4 · {{etapas[etapa-1]}}</p></div><button type="button" class="secondary" [disabled]="ocupado" (click)="sair()">Fechar</button></header>
 <ol class="etapas" aria-label="Etapas do preenchimento">@for(nome of etapas;track nome;let i=$index){<li [class.atual]="etapa===i+1" [attr.aria-current]="etapa===i+1?'step':null">{{i+1}}. {{nome}}</li>}</ol>
 <form #formulario="ngForm" class="formulario" (ngSubmit)="avancar()">
 @if(erro){<p role="alert" class="error">{{erro}}</p>}
 @if(etapa===1){
  <h3>Escolha o modelo</h3>
  <div class="opcoes">
   @for(modelo of modelos;track modelo.id){
    <label class="opcao" [class.escolhida]="pedido.modelo===modelo.id"><input type="radio" name="modelo" [value]="modelo.id" [checked]="pedido.modelo===modelo.id" (change)="pedido.modelo=modelo.id"><strong>{{modelo.nome}}</strong><span>{{modelo.descricao}}</span></label>
   }
  </div>
 }@else if(etapa===2){
  <h3>Identificação e datas</h3>
  <div class="campos">
   <label>Instituição<input name="instituicao" [(ngModel)]="pedido.instituicao" maxlength="250" required></label>
   <label>Cidade<input name="cidade" [(ngModel)]="pedido.cidade" maxlength="150" required></label>
   <label>{{pedido.modelo==='COMPARECIMENTO'?'Data da aula':'Data da reunião'}}<input type="date" name="data" [(ngModel)]="pedido.data" required></label>
   <label>Local<input name="local" [(ngModel)]="pedido.local" maxlength="300" required></label>
   <label>Início<input type="time" name="inicio" [(ngModel)]="pedido.inicio" required></label>
   <label>Término<input type="time" name="fim" [(ngModel)]="pedido.fim" [required]="pedido.modelo==='COMPARECIMENTO'"></label>
   @if(pedido.modelo==='NOMEACAO'){
    <label>Curso<input name="curso" [(ngModel)]="pedido.curso" maxlength="200" required></label>
    <label>Duração do mandato<input name="mandato" [(ngModel)]="pedido.mandato" placeholder="Ex.: um ano, a partir desta data" maxlength="300" required></label>
   }
   @if(pedido.modelo==='COMPARECIMENTO'){<label>Data de emissão<input type="date" name="emissao" [(ngModel)]="pedido.dataEmissao" [min]="pedido.data" required></label>}
   @if(pedido.modelo==='ASSUNTO'){<label class="largo">Assunto da reunião<input name="assunto" [(ngModel)]="pedido.assunto" maxlength="150" placeholder="Ex.: planejamento da semana acadêmica" required></label>}
  </div>
 }@else if(etapa===3){
  @if(pedido.modelo==='NOMEACAO'){
   <h3>Composição do Centro Acadêmico</h3>
   @for(membro of pedido.membros;track $index;let i=$index){
    <fieldset><legend>Integrante {{i+1}}</legend><div class="campos">
     <label>Cargo<input [name]="'cargo'+i" [(ngModel)]="membro.cargo" maxlength="150" required></label>
     <label>Nome completo<input [name]="'nome'+i" [(ngModel)]="membro.nome" maxlength="250" required></label>
     <label>Período ou turma<input [name]="'periodo'+i" [(ngModel)]="membro.periodo" maxlength="100" placeholder="Ex.: 4º período"></label>
    </div><button type="button" class="danger-link" [disabled]="pedido.membros.length===1" (click)="pedido.membros.splice(i,1)">Remover integrante</button></fieldset>
   }
   <button type="button" class="secondary" [disabled]="pedido.membros.length>=20" (click)="pedido.membros.push({cargo:'',nome:'',periodo:''})">Adicionar integrante</button>
   <label>Registro da nomeação e deliberações<textarea name="decisoes" [(ngModel)]="pedido.decisoes" maxlength="20000" placeholder="Informe como ocorreu a escolha, o início do mandato e o que foi decidido." required></textarea></label>
  }@else if(pedido.modelo==='COMPARECIMENTO'){
   <h3>Participante e responsável</h3><div class="campos">
    <label class="largo">Nome completo do participante<input name="participante" [(ngModel)]="pedido.nomeParticipante" maxlength="250" required></label>
    <label>RG<input name="rg" [(ngModel)]="pedido.rg" maxlength="30" required></label>
    <label>CPF<input name="cpf" [(ngModel)]="pedido.cpf" maxlength="20" required></label>
    <label class="largo">Responsável pela emissão<input name="responsavel" [(ngModel)]="pedido.responsavel" maxlength="250" required></label>
   </div>
  }@else{
   <h3>O que aconteceu na reunião</h3>
   <p>Descreva os fatos e as decisões. Informações ausentes serão destacadas para preenchimento.</p>
   <label>Participantes<textarea name="participantes" [(ngModel)]="pedido.participantes" maxlength="10000" placeholder="Nomes dos participantes"></textarea></label>
   <div class="campos"><label>Quem conduziu a reunião<input name="responsavel" [(ngModel)]="pedido.responsavel" maxlength="250"></label><label>Quem redige a ata<input name="secretario" [(ngModel)]="pedido.secretario" maxlength="250"></label></div>
   <label>Discussões<textarea name="discussoes" [(ngModel)]="pedido.discussoes" maxlength="20000"></textarea></label>
   <label>Decisões, responsáveis e prazos<textarea name="decisoes" [(ngModel)]="pedido.decisoes" maxlength="20000"></textarea></label>
  }
 }@else{
  <h3>Revise o documento</h3>
  <p>Confira os nomes, os fatos e as datas. As assinaturas deverão ser feitas após a geração.</p>
  @if(pedido.modelo==='ASSUNTO'){<button type="button" class="secondary" [disabled]="ocupado" (click)="organizar()">Organizar rascunho com IA</button>}
  <label>Título<input name="titulo" [(ngModel)]="pedido.titulo" (ngModelChange)="pedido.revisado=false" maxlength="300" required></label>
  <label>Texto do documento<textarea class="revisao" name="texto" [(ngModel)]="pedido.texto" (ngModelChange)="pedido.revisado=false" maxlength="60000" required></textarea></label>
  <details><summary>Visualizar a organização do documento</summary>
   <article class="folha" [class.declaracao]="pedido.modelo==='COMPARECIMENTO'">
    @if(usaCabecalhoFamper()){<img [src]="api.base+'/atas-atividades/modelos/cabecalho'" alt="Cabeçalho da FAMPER">}@else{<p>{{pedido.instituicao}}</p>}
    <h3>{{pedido.titulo}}</h3>@for(paragrafo of paragrafos();track $index){<p class="texto" [class.integrante]="ehIntegrante(paragrafo)">{{paragrafo}}</p>}
    @for(assinatura of assinaturas;track $index){<div class="assinatura"><span>________________________________</span><div>{{assinatura.nome}}</div><div>{{assinatura.cargo}}</div></div>}
   </article>
  </details>
  @if(pedido.texto.includes('[PREENCHER')){<p class="error">Existem campos marcados com [PREENCHER]. Confira-os antes de usar o documento.</p>}
  <label class="check"><input type="checkbox" name="revisado" [(ngModel)]="pedido.revisado"> Revisei o conteúdo e confirmo as informações.</label>
 }
 <footer class="acoes">
  @if(etapa>1){<button type="button" class="secondary" [disabled]="ocupado" (click)="voltar()">Voltar</button>}
  @if(etapa<4){<button type="submit" [disabled]="ocupado||formulario.invalid">{{ocupado?'Preparando…':'Continuar'}}</button>}
  @else{
   <button type="button" class="secondary" [disabled]="ocupado||!pedido.revisado||formulario.invalid" (click)="baixar()">Baixar Word</button>
   <button type="button" [disabled]="ocupado||!pedido.revisado||formulario.invalid" (click)="salvar()">{{ocupado?'Processando…':'Salvar em Atas e Atividades'}}</button>
  }
 </footer>
 </form>
</dialog>`,styles:[`
.assistente{width:min(1000px,calc(100vw - 32px));max-height:calc(100dvh - 32px);padding:0;border:1px solid #dbe3ec;border-radius:12px;color:#24334b}
.assistente::backdrop{background:#10243baa}
.topo{display:flex;justify-content:space-between;align-items:center;gap:16px;height:auto;text-align:left;padding:22px 28px}
.topo h2{margin:0}.topo p{margin:6px 0 0}
.etapas{display:flex;gap:16px;padding:16px 28px;margin:0;list-style:none;background:#f3f6fa;flex-wrap:wrap}.atual{color:#125aa8;font-weight:700}
.formulario{width:100%;padding:24px 28px;margin:0;box-shadow:none;border-radius:0}
.opcoes{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.opcao{border:1px solid #bdc9d7;padding:18px;border-radius:10px;cursor:pointer;gap:12px;font-weight:400}.opcao input{align-self:flex-start}.escolhida{border-color:#1769c2;background:#e8f1fb}
.campos{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.largo{grid-column:1/-1}
fieldset{border:1px solid #dbe3ec;border-radius:8px;margin:14px 0;padding:16px}input,textarea,select{min-width:0;width:100%}.check input{width:auto}
.revisao{min-height:340px;font-family:'Times New Roman',serif;font-weight:400;line-height:1.5}
.acoes{display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;padding-top:20px;margin-top:20px;border-top:1px solid #dbe3ec}
.folha{padding:28px;margin:20px 0;background:white;overflow-wrap:anywhere;font-family:'Times New Roman',serif;font-size:12pt}.folha.declaracao{font-family:Arial,sans-serif}.folha img{width:100%;height:auto}.folha h3{text-align:center;font-size:12pt;line-height:1;margin:28px 0 12pt}.texto{white-space:pre-wrap;line-height:1.5;text-align:justify;text-indent:1.25cm;margin:0 0 6pt}.texto.integrante{text-indent:0;margin-bottom:0}.assinatura{margin-top:24pt;line-height:1}
button:disabled{opacity:.6;cursor:wait}
@media(max-width:700px){.opcoes,.campos{grid-template-columns:1fr}.topo,.formulario{padding:18px}.acoes button{width:100%}}
`]})
export class CriadorDocumentoComponent implements AfterViewInit{
 @Output() fechar=new EventEmitter<void>();
 @Output() salvo=new EventEmitter<void>();
 @ViewChild('janela') janela!:ElementRef<HTMLDialogElement>;
 etapa=1;ocupado=false;erro='';assinaturas:Membro[]=[];private ultimaPrevia='';
 readonly etapas=['Modelo','Identificação','Conteúdo','Revisão'];
 readonly modelos=[
  {id:'NOMEACAO',nome:'Ata de nomeação',descricao:'Centro Acadêmico, cargos, integrantes e mandato. Baseada no modelo enviado.'},
  {id:'COMPARECIMENTO',nome:'Declaração de comparecimento',descricao:'Participação em aula, horários e responsável. Baseada no modelo enviado.'},
  {id:'ASSUNTO',nome:'Ata por assunto',descricao:'Reunião sobre um tema escolhido, com revisão e organização opcional por IA.'}
 ];
 pedido:Pedido={
  modelo:'NOMEACAO',instituicao:'Faculdade de Ampére - FAMPER',cidade:'Ampére/PR',curso:'',data:this.hoje(),inicio:'',fim:'',
  local:'',assunto:'',participantes:'',discussoes:'',decisoes:'',responsavel:'',secretario:'',mandato:'um ano, a partir desta data',
  nomeParticipante:'',rg:'',cpf:'',dataEmissao:this.hoje(),membros:['Presidente','Vice-presidente','Secretário Geral','Secretário Adjunto','1º Suplente','2º Suplente'].map(cargo=>({cargo,nome:'',periodo:''})),
  titulo:'',texto:'',revisado:false,registroId:Date.now()*1000+Math.floor(Math.random()*1000)
 };
 constructor(public api:ApiService,private http:HttpClient,private zone:NgZone,private view:ChangeDetectorRef){}
 ngAfterViewInit(){this.janela.nativeElement.showModal()}
 private hoje(){
  const p=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  return ['year','month','day'].map(tipo=>p.find(parte=>parte.type===tipo)!.value).join('-');
 }
 sair(){if(!this.ocupado)this.fechar.emit()}
 usaCabecalhoFamper(){return /\bfamper\b/i.test(this.pedido.instituicao)}
 paragrafos(){return this.pedido.texto.split(/\r?\n/).filter(linha=>linha.trim())}
 ehIntegrante(linha:string){return this.pedido.modelo==='NOMEACAO'&&this.pedido.membros.some(m=>linha.startsWith(m.cargo+': '+m.nome))}
 clicarFundo(event:MouseEvent){if(event.target===this.janela.nativeElement)this.sair()}
 cancelar(event:Event){if(this.ocupado)event.preventDefault();else this.fechar.emit()}
 voltar(){this.etapa--;this.erro='';this.pedido.revisado=false}
 private atualizar(){this.zone.run(()=>this.view.markForCheck())}
 private dados(){
  return {...this.pedido,fim:this.pedido.fim||null,dataEmissao:this.pedido.dataEmissao||null};
 }
 async avancar(){
  if(this.ocupado)return;
  this.erro='';
  if(this.etapa<3){this.etapa++;return}
  this.ocupado=true;
  try{
   const chave=JSON.stringify({...this.pedido,titulo:null,texto:null,revisado:false,registroId:null});
   if(chave!==this.ultimaPrevia){
    const previa=await firstValueFrom(this.http.post<Previa>(this.api.base+'/atas-atividades/modelos/previa',this.dados(),{withCredentials:true}).pipe(timeout(15000)));
    this.pedido.titulo=previa.titulo;this.pedido.texto=previa.texto;this.assinaturas=previa.assinaturas;this.ultimaPrevia=chave;
   }
   this.pedido.revisado=false;this.etapa=4;
  }catch(e){this.falha(e)}finally{this.ocupado=false;this.atualizar()}
 }
 async organizar(){
  if(this.ocupado)return;this.ocupado=true;this.erro='';
  try{
   const previa=await firstValueFrom(this.http.post<Previa>(this.api.base+'/atas-atividades/modelos/organizar',this.dados(),{withCredentials:true}).pipe(timeout(125000)));
   this.pedido.texto=previa.texto;this.pedido.revisado=false;
  }catch(e){this.falha(e)}finally{this.ocupado=false;this.atualizar()}
 }
 async baixar(){
  if(this.ocupado||!this.pedido.revisado)return;this.ocupado=true;this.erro='';
  try{
   const arquivo=await firstValueFrom(this.http.post(this.api.base+'/atas-atividades/modelos/baixar',this.dados(),{withCredentials:true,responseType:'blob'}).pipe(timeout(20000)));
   const url=URL.createObjectURL(arquivo),link=document.createElement('a');link.href=url;
   link.download=this.pedido.modelo==='COMPARECIMENTO'?'declaracao-comparecimento.docx':this.pedido.modelo==='NOMEACAO'?'ata-nomeacao.docx':'ata-por-assunto.docx';
   link.click();setTimeout(()=>URL.revokeObjectURL(url),60000);
  }catch(e){await this.falhaDownload(e)}finally{this.ocupado=false;this.atualizar()}
 }
 async salvar(){
  if(this.ocupado||!this.pedido.revisado)return;this.ocupado=true;this.erro='';
  try{
   await firstValueFrom(this.http.post(this.api.base+'/atas-atividades/modelos/salvar',this.dados(),{withCredentials:true}).pipe(timeout(30000)));
   this.zone.run(()=>this.salvo.emit());
  }catch(e){this.falha(e)}finally{this.ocupado=false;this.atualizar()}
 }
 private falha(e:any){this.erro=e.error?.erro||(e.name==='TimeoutError'?'A operação demorou. Tente novamente.':'Não foi possível gerar o documento. Confira a conexão e os campos.')}
 private async falhaDownload(e:any){
  if(e.error instanceof Blob){try{this.erro=JSON.parse(await e.error.text()).erro;return}catch{}}
  this.falha(e);
 }
}

import {AuthService} from '../services/auth.service';
import {ChangeDetectorRef,Component,NgZone,OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {firstValueFrom,timeout,TimeoutError} from 'rxjs';
import {ApiService} from '../services/api.service';

type Status='PENDENTE'|'APROVADO'|'REPROVADO';
interface Avaliacao{status:Status;horasAprovadas:number|null;observacao:string|null;aprovadoEm?:string|null}
interface Certificado extends Avaliacao{id:string;alunoNome:string;nome:string;arquivoNome:string;enviadoEm:string}
interface PastaDrive{id:string;nome:string;filhas?:PastaDrive[];certificados:{id:string;nome:string;enviadoEm:string}[]}

@Component({selector:'app-certificados',standalone:true,imports:[CommonModule,FormsModule,RouterLink],template:`
<section><div class="title"><div><h1>Certificados</h1><p>Certificados enviados pelos alunos na pasta do Google Drive</p></div><div>@if(auth.admin){<button class="secondary" [disabled]="exportando||sincronizando" (click)="erroRelatorio='' ;relatorioAberto=true">{{exportando?'Exportando…':'Exportar Excel'}}</button>}<button (click)="sincronizar()" [disabled]="sincronizando">{{sincronizando?'Atualizando...':(auth.admin?'Atualizar do Drive':'Atualizar')}}</button></div></div>
@if(origem){<div class="notice"><strong>Pasta de certificados vinculada permanentemente</strong><div><a [href]="origem.url" target="_blank" rel="noopener noreferrer">Abrir pasta no Google Drive</a>@if(auth.admin){ · <a routerLink="/google-drive">Configurar conexão</a>}</div></div>}
@if(mensagem){<div class="notice" role="status">{{mensagem}}</div>}
<div class="filtros-certificados" role="search" aria-label="Filtrar certificados">
 <label class="busca-certificados">Aluno ou certificado<input type="search" name="buscaCertificados" [(ngModel)]="buscaCertificados" placeholder="Digite um nome ou título"></label>
 <label>Situação<select name="situacaoCertificados" [(ngModel)]="situacaoCertificados"><option value="">Todas</option><option value="PENDENTE">Pendente</option><option value="APROVADO">Aprovado</option><option value="REPROVADO">Reprovado</option></select></label>
 <label>Aprovado a partir de<input type="date" name="aprovadoDe" [(ngModel)]="aprovadoDe" [max]="aprovadoAte"></label>
 <label>Aprovado até<input type="date" name="aprovadoAte" [(ngModel)]="aprovadoAte" [min]="aprovadoDe"></label>
 <button type="button" class="secondary" [disabled]="!filtrosAtivos" (click)="limparFiltros()">Limpar filtros</button>
 <p class="ajuda-filtros">As datas consideram a aprovação das horas. Aprovações antigas sem data aparecem quando o período está em branco.</p>
</div>
@if(periodoInvalido){<p class="error" role="alert">A data final deve ser igual ou posterior à inicial.</p>}
@if(filtrosAtivos){<p role="status">{{certificados.length}} de {{totalCertificados}} certificados encontrados.</p>}
<div class="certificate-stats"><article><small>Alunos identificados</small><strong>{{gruposFiltrados.length}}</strong></article><article><small>Certificados</small><strong>{{certificados.length}}</strong></article><article><small>Aguardando avaliação</small><strong>{{pendentes}}</strong></article></div>
@if(!grupos.length){<div class="empty">Nenhum certificado disponível. Atualize os dados da pasta vinculada.</div>}
@if(grupos.length&&!gruposFiltrados.length){<div class="empty">Nenhum certificado encontrado para os filtros selecionados.</div>}
@for(grupo of gruposFiltrados;track grupo.vinculo + grupo.id){<div class="student-certificates"><div class="student-heading"><div><span>Aluno</span><h2>{{grupo.aluno}}</h2></div><div><span>{{filtrosAtivos?'Horas aprovadas no filtro':'Horas aprovadas'}}</span><strong>{{totalAluno(grupo.itens)}}h</strong>@if(auth.admin&&grupo.vinculo!==origem?.id){<button type="button" class="link" [disabled]="sincronizando" (click)="desvincular(grupo.vinculo)">Desvincular origem</button>}</div></div><div class="table-wrap"><table><thead><tr><th>Certificado</th><th>Arquivo no Drive</th><th>Enviado em</th><th>Aprovado em</th><th>Horas</th><th>Status</th><th>Ações</th></tr></thead><tbody>
@for(c of grupo.itens;track c.id){<tr><td><strong>{{c.nome}}</strong>@if(c.observacao){<small class="row-note">{{c.observacao}}</small>}</td><td>{{c.arquivoNome}}</td><td>{{c.enviadoEm|date:'dd/MM/yyyy HH:mm'}}</td><td>{{c.aprovadoEm?(c.aprovadoEm|date:'dd/MM/yyyy HH:mm'):(c.status==='APROVADO'?'Sem data':'—')}}</td><td>{{c.horasAprovadas==null?'—':c.horasAprovadas+'h'}}</td><td><span class="badge status-{{c.status}}">{{c.status}}</span></td><td class="row-actions"><button class="link" (click)="visualizar(c)">Visualizar</button><button class="link" (click)="abrirAprovacao(c)">Aprovar</button>@if(auth.admin){<button class="danger-link" (click)="abrirReprovacao(c)">Reprovar</button>}</td></tr>}
@empty{<tr><td colspan="7">Nenhum PDF encontrado nesta pasta.</td></tr>}
</tbody></table></div></div>}</section>
@if(relatorioAberto){<div class="modal"><form #filtro="ngForm" (ngSubmit)="exportar()"><h2>Exportar horas aprovadas</h2><p>Somente certificados aprovados entram na soma. Selecione o período pela data da aprovação.</p><label class="check"><input type="checkbox" name="historico" [(ngModel)]="historicoCompleto"> Todo o histórico</label><label>Data inicial<input type="date" name="inicioRelatorio" [(ngModel)]="inicioRelatorio" [disabled]="historicoCompleto||exportando" [required]="!historicoCompleto" [max]="fimRelatorio"></label><label>Data final<input type="date" name="fimRelatorio" [(ngModel)]="fimRelatorio" [disabled]="historicoCompleto||exportando" [required]="!historicoCompleto" [min]="inicioRelatorio"></label><p><small>Aprovações antigas sem data registrada aparecem apenas em “Todo o histórico”.</small></p>@if(erroRelatorio){<p class="error" role="alert">{{erroRelatorio}}</p>}<div class="actions"><button type="button" class="secondary" [disabled]="exportando" (click)="relatorioAberto=false">Cancelar</button><button type="submit" [disabled]="exportando||filtro.invalid||(!historicoCompleto&&inicioRelatorio>fimRelatorio)">{{exportando?'Exportando…':'Baixar Excel'}}</button></div></form></div>}
@if(avaliando&&modo==='aprovar'){<div class="modal"><form (ngSubmit)="aprovar()"><h2>Aprovar certificado</h2><p><strong>{{avaliando.nome}}</strong><br>{{avaliando.alunoNome}}</p><label>Quantidade de horas aprovadas<input type="number" min="0" required name="horas" [(ngModel)]="horas"></label><div class="actions"><button type="button" class="secondary" (click)="fechar()">Cancelar</button><button type="submit">Confirmar aprovação</button></div></form></div>}
@if(avaliando&&modo==='reprovar'){<div class="modal"><form (ngSubmit)="reprovar()"><h2>Reprovar certificado</h2><p><strong>{{avaliando.nome}}</strong><br>{{avaliando.alunoNome}}</p><label>Motivo da reprovação<textarea required name="motivo" [(ngModel)]="motivo"></textarea></label><div class="actions"><button type="button" class="secondary" (click)="fechar()">Cancelar</button><button type="submit" class="danger">Confirmar reprovação</button></div></form></div>}`,
styles:[`.filtros-certificados{display:flex;align-items:flex-end;flex-wrap:wrap;gap:14px;padding:18px;background:#fff;border:1px solid #dbe3ec;border-radius:9px;margin-bottom:18px}.filtros-certificados label{margin:0;flex:1 1 150px;min-width:0}.filtros-certificados .busca-certificados{flex:2 1 260px}.filtros-certificados input,.filtros-certificados select{width:100%;min-width:0}.ajuda-filtros{flex-basis:100%;margin:0;color:#617087;font-size:13px}.drive-warning,.notice{display:flex;flex-direction:column;gap:5px;padding:15px 18px;border-radius:8px;margin-bottom:18px}.drive-warning{background:#fff8df;border:1px solid #ecd487;color:#684f00}.notice{background:#e8f1fb;color:#125aa8}.certificate-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:22px}.certificate-stats article{display:flex;flex-direction:column;gap:8px}.certificate-stats strong{font-size:25px;color:#125aa8}.student-certificates{margin:18px 0 28px}.student-heading{display:flex;justify-content:space-between;align-items:center;background:#e8f1fb;border:1px solid #cbddef;border-radius:9px 9px 0 0;padding:14px 18px}.student-heading h2{font-size:19px;margin:3px 0}.student-heading span{font-size:12px;color:#617087}.student-heading>div:last-child{text-align:right}.student-heading>div:last-child strong{font-size:22px;color:#125aa8}.student-certificates .table-wrap{border-radius:0 0 9px 9px}.row-note{display:block;margin-top:5px;color:#a62525}.row-actions{white-space:nowrap}.row-actions button{font-size:13px}button:disabled{opacity:.65;cursor:wait}@media(max-width:900px){.certificate-stats{grid-template-columns:1fr 1fr}}`]})
export class CertificadosComponent implements OnInit{
 buscaCertificados='';situacaoCertificados:''|Status='';aprovadoDe='';aprovadoAte='';
 get filtrosAtivos(){return !!(this.buscaCertificados.trim()||this.situacaoCertificados||this.aprovadoDe||this.aprovadoAte)}
 get periodoInvalido(){return !!(this.aprovadoDe&&this.aprovadoAte&&this.aprovadoDe>this.aprovadoAte)}
 limparFiltros(){this.buscaCertificados='';this.situacaoCertificados='';this.aprovadoDe='';this.aprovadoAte=''}
 private normalizar(texto:string){return texto.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('pt-BR').replace(/\s+/g,' ').trim()}
 get gruposFiltrados(){
  if(this.periodoInvalido)return [];
  if(!this.filtrosAtivos)return this.grupos;
  const termo=this.normalizar(this.buscaCertificados),periodo=!!(this.aprovadoDe||this.aprovadoAte);
  return this.grupos.map(grupo=>({...grupo,itens:grupo.itens.filter(c=>{
   if(termo&&!this.normalizar(grupo.aluno+' '+c.nome+' '+c.arquivoNome).includes(termo))return false;
   if(this.situacaoCertificados&&c.status!==this.situacaoCertificados)return false;
   if(periodo){
    if(c.status!=='APROVADO'||!c.aprovadoEm)return false;
    const data=new Date(c.aprovadoEm);if(!Number.isFinite(data.getTime()))return false;
    const dia=this.diaLocal(data);
    if((this.aprovadoDe&&dia<this.aprovadoDe)||(this.aprovadoAte&&dia>this.aprovadoAte))return false;
   }
   return true;
  })})).filter(grupo=>grupo.itens.length||(!this.situacaoCertificados&&!periodo&&this.normalizar(grupo.aluno).includes(termo)));
 }
 relatorioAberto=false;historicoCompleto=false;erroRelatorio='';
 inicioRelatorio=this.hoje().slice(0,7)+'-01';fimRelatorio=this.hoje();
 private hoje(){
  return this.diaLocal(new Date());
 }
 private diaLocal(data:Date){
  const partes=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(data);
  return ['year','month','day'].map(tipo=>partes.find(p=>p.type===tipo)!.value).join('-');
 }
 exportando=false;sincronizando=false;vinculando=false;mensagem='';avaliando?:Certificado;modo?:'aprovar'|'reprovar';horas:number|null=null;motivo='';
 origem?:{id:string;url:string};
 grupos:{id:string;vinculo:string;aluno:string;itens:Certificado[]}[]=[];

 private dados:{pastas:string[];avaliacoes:Record<string,Avaliacao>}={pastas:[],avaliacoes:{}};

 constructor(private http:HttpClient,private api:ApiService,private zone:NgZone,private view:ChangeDetectorRef,public auth:AuthService){}

 ngOnInit(){void this.iniciar()}
 private async iniciar(){
  try{this.origem=await firstValueFrom(this.http.get<{id:string;url:string}>(this.api.base+'/certificados/origem',{withCredentials:true}).pipe(timeout(15000)))}
  catch(e){this.mensagem=this.mensagemErro(e)}
  await this.carregarSalvos();
  if(this.origem)await this.carregarSalvos(true);
 }
 get totalCertificados(){return this.grupos.reduce((total,g)=>total+g.itens.length,0)}
 get certificados(){return this.gruposFiltrados.flatMap(grupo=>grupo.itens)}
 get pendentes(){return this.certificados.filter(c=>c.status==='PENDENTE').length}

 totalAluno(itens:Certificado[]){return itens.reduce((s,c)=>s+(c.status==='APROVADO'?(c.horasAprovadas||0):0),0)}

 async sincronizar(){
  if(this.sincronizando)return;
  await this.carregarSalvos(true);
 }
 async desvincular(id:string){
  if(!this.auth.admin)return;
  try{
   await firstValueFrom(this.http.delete(this.api.base+'/certificados/pastas/'+encodeURIComponent(id),{withCredentials:true}));
   this.dados={...this.dados,pastas:this.dados.pastas.filter(pasta=>pasta!==id)};
   this.grupos=this.grupos.filter(grupo=>grupo.vinculo!==id);
   this.mensagem='Pasta desvinculada.';
  }catch(erro){this.mensagem=this.mensagemErro(erro)}
 }

 private async carregarSalvos(atualizar=false){
  this.sincronizando=true;
  try{
   const dados=await firstValueFrom(this.http.get<{pastas:Record<string,PastaDrive>;avaliacoes:Record<string,Avaliacao>}>(this.api.base+(atualizar?'/certificados/sincronizar':'/certificados'),{withCredentials:true}).pipe(timeout(45000)));
   const ids=Object.keys(dados.pastas);
   this.dados={pastas:ids,avaliacoes:dados.avaliacoes};
   this.grupos=Object.values(dados.pastas).flatMap(p=>this.montarGrupos(p));

  }catch(e){this.mensagem=this.mensagemErro(e)}
  finally{this.finalizarConsulta()}
 }


 private finalizarConsulta(){
  this.zone.run(()=>{
   this.sincronizando=false;
   this.vinculando=false;
   this.view.markForCheck();
  });
 }

 private montarGrupos(pasta:PastaDrive){
  const filhas=pasta.filhas??[];
  const pastas=filhas.length && !pasta.certificados.length?filhas:[pasta,...filhas];
  return pastas.map(item=>({...this.montarGrupo(item),vinculo:pasta.id}));
 }
 private montarGrupo(pasta:PastaDrive){
  const prefixo=pasta.nome+' - ';
  const itens=pasta.certificados.map(arquivo=>{
   const titulo=arquivo.nome.replace(/\.pdf$/i,'');
   const nome=titulo.toLocaleLowerCase().startsWith(prefixo.toLocaleLowerCase())?titulo.slice(prefixo.length):titulo;
   const avaliacao=this.dados.avaliacoes[arquivo.id]??{status:'PENDENTE' as const,horasAprovadas:null,observacao:null};
   return {...avaliacao,id:arquivo.id,alunoNome:pasta.nome,nome,arquivoNome:prefixo+nome+'.pdf',enviadoEm:arquivo.enviadoEm};
  });
  return {id:pasta.id,aluno:pasta.nome,itens};
 }

 private mensagemErro(erro:unknown){
  if(erro instanceof TimeoutError)return 'O Google Drive demorou a responder. Tente novamente.';
  const status=(erro as {status?:number})?.status;
  if(status===0)return 'Não foi possível acessar o servidor. Abra o projeto em localhost:4200 e confira se o backend está rodando.';
  if(status===401)return this.auth.admin?'Entre novamente e confira a conexão com o Google Drive.':'Entre novamente para avaliar os certificados.';
  if(status===404)return 'Não foi possível acessar uma das pastas. Confira o link e se a conta conectada tem acesso.';
  return (erro as any)?.error?.erro||'Não foi possível concluir a operação. Confira a conexão e tente novamente.';
 }



 visualizar(c:Certificado){window.open('https://drive.google.com/file/d/'+encodeURIComponent(c.id)+'/view','_blank','noopener,noreferrer')}
 abrirAprovacao(c:Certificado){this.avaliando=c;this.modo='aprovar';this.horas=c.horasAprovadas}abrirReprovacao(c:Certificado){this.avaliando=c;this.modo='reprovar';this.motivo=c.observacao||''}fechar(){this.avaliando=undefined;this.modo=undefined}
 aprovar(){if(this.horas==null||!Number.isFinite(this.horas)||this.horas<0)return;this.avaliar({status:'APROVADO',horasAprovadas:this.horas,observacao:null})}
 reprovar(){if(!this.motivo.trim())return;this.avaliar({status:'REPROVADO',horasAprovadas:null,observacao:this.motivo.trim()})}
 private async avaliar(avaliacao:Avaliacao){
  const certificado=this.avaliando;
  if(!certificado)return;
  try{
   const salva=await firstValueFrom(this.http.put<Avaliacao>(this.api.base+'/certificados/'+encodeURIComponent(certificado.id)+'/avaliacao',avaliacao,{withCredentials:true}));
   this.dados.avaliacoes[certificado.id]=salva;
   Object.assign(certificado,salva);
   this.fechar();
  }catch(erro){this.mensagem=this.mensagemErro(erro)}
  finally{this.zone.run(()=>this.view.markForCheck())}
 }
 async exportar(){
  if(!this.auth.admin||this.exportando)return;
  if(!this.historicoCompleto&&(!this.inicioRelatorio||!this.fimRelatorio||this.inicioRelatorio>this.fimRelatorio)){this.erroRelatorio='Informe um período válido.';return}
  const params:Record<string,string>={};
  if(!this.historicoCompleto){params['inicio']=this.inicioRelatorio;params['fim']=this.fimRelatorio}
  this.exportando=true;
  this.erroRelatorio='';
  try{
   const arquivo=await firstValueFrom(this.http.get(this.api.base+'/certificados/relatorio',{withCredentials:true,responseType:'blob',params}).pipe(timeout(30000)));
   const url=URL.createObjectURL(arquivo),a=document.createElement('a');
   a.href=url;a.download='horas-aprovadas.xlsx';a.click();
   this.relatorioAberto=false;
   setTimeout(()=>URL.revokeObjectURL(url),60000);
  }catch{this.erroRelatorio='Não foi possível exportar o relatório. Confira a conexão e tente novamente.'}
  finally{this.exportando=false;this.zone.run(()=>this.view.markForCheck())}
 }
}

import {Component,ElementRef,OnInit,OnDestroy,ViewChild} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ApiService} from '../services/api.service';
import {AuthService} from '../services/auth.service';
@Component({standalone:true,imports:[FormsModule,DatePipe],styles:[`
 .cards {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  align-items: start;
 }
 .cards article {
  min-width: 0;
  overflow-wrap: anywhere;
 }
 .cards article p {
  white-space: pre-wrap;
  line-height: 1.5;
 }
 .aviso-leitura {
  width: min(680px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  padding: 0;
  border: 1px solid #dbe3ec;
  border-radius: 12px;
  color: #24334b;
  box-shadow: 0 18px 60px #10243b44;
  overflow-wrap: anywhere;
 }
 .aviso-leitura::backdrop { background: #10243b88; }
 .leitura-conteudo { padding: 28px; }
 .leitura-conteudo h2 { margin: 12px 0; }
 .leitura-conteudo p { white-space: pre-wrap; line-height: 1.6; }
 .leitura-acoes {
  position: sticky;
  bottom: 0;
  padding: 16px 28px;
  background: white;
  border-top: 1px solid #dbe3ec;
  text-align: right;
 }
 `],template:`
<section><div class="title"><div><h1>Avisos</h1><p>Comunicados por curso</p></div>@if(auth.editorAvisos){<button (click)="editar()">Novo aviso</button>}</div>
<dialog #leitura class="aviso-leitura" aria-labelledby="leitura-titulo" (click)="$event.target === leitura && fecharLeitura()">
 <div class="leitura-conteudo">
  <small>{{avisoSelecionado?.curso}} · Expira em {{avisoSelecionado?.data|date:'dd/MM/yyyy'}}</small>
  <h2 id="leitura-titulo">{{avisoSelecionado?.titulo}}</h2>
  <p>{{avisoSelecionado?.descricao}}</p>
 </div>
 <div class="leitura-acoes"><button type="button" autofocus (click)="fecharLeitura()">Fechar</button></div>
</dialog><label>Curso<select [(ngModel)]="filtro"><option value="">Todos os cursos</option>@for(c of cursos;track c){<option [value]="c">{{c}}</option>}</select></label>
@if(auth.editorAvisos){<label>Situação<select [(ngModel)]="situacao" (ngModelChange)="listar()"><option value="ativos">Ativos</option><option value="expirados">Expirados</option><option value="todos">Todos</option></select></label>}
@if(erro){<p class="error" role="alert">{{erro}}</p>}
@for(c of cursosVisiveis;track c){<h2>{{c==='Geral'?'Avisos gerais':c}}</h2><div class="cards">
@for(a of porCurso(c);track a.id){<article><small>{{expirado(a)?'Expirou em':'Expira em'}} {{a.data|date:'dd/MM/yyyy'}}</small><h3>{{a.titulo}}</h3><p [id]="'aviso-texto-'+a.id">{{textoExibido(a)}}</p>@if(avisoLongo(a.descricao)){<button type="button" class="link" aria-haspopup="dialog" (click)="abrirLeitura(a)">Ler mais</button>}@if(auth.editorAvisos){<button class="link" (click)="editar(a)">Editar</button><button class="danger-link" (click)="excluir(a.id)">Excluir</button>}</article>}@empty{<div class="empty">Nenhum aviso publicado.</div>}</div>}
@if(aberto && auth.editorAvisos){<div class="modal"><form (ngSubmit)="salvar()"><h2>{{form.id?'Editar':'Novo'}} aviso</h2><label>Curso<select name="curso" [(ngModel)]="form.curso" required>@for(c of cursos;track c){<option [value]="c">{{c}}</option>}</select></label><label>Título<input name="titulo" [(ngModel)]="form.titulo" required></label><label>Descrição<textarea name="descricao" [(ngModel)]="form.descricao" required></textarea></label><label>Data de expiração<input type="date" [min]="dataMinima" name="data" [(ngModel)]="form.data" required></label><small>O aviso ficará visível até o fim do dia escolhido, no horário de Brasília.</small>@if(erro){<p class="error">{{erro}}</p>}<div class="actions"><button type="button" class="secondary" (click)="aberto=false">Cancelar</button><button [disabled]="salvando">Salvar</button></div></form></div>}</section>`})
export class AvisosComponent implements OnInit,OnDestroy {
 situacao='ativos';private timer?:ReturnType<typeof setInterval>;
 readonly cursos=['Geral','Administração','Ciência da Computação','Ciências Contábeis','Educação Física','Engenharia Civil','Pedagogia','Psicologia','Análise e Desenvolvimento de Sistemas'];
 avisos:any[]=[];filtro='';aberto=false;salvando=false;erro='';form:any={};
 @ViewChild('leitura') private leitura!:ElementRef<HTMLDialogElement>;
 avisoSelecionado:any=null;
 private previa(texto:string):string {
  const linhas=texto.split(/\r?\n/);
  const trecho=linhas.slice(0,4).join('\n');
  const caracteres=Array.from(trecho);
  if(caracteres.length<=240)return trecho;
  const limite=caracteres.slice(0,240).join('');
  const espaco=limite.lastIndexOf(' ');
  return espaco>180?limite.slice(0,espaco):limite;
 }
 avisoLongo(texto:string){return this.previa(texto).length<texto.replace(/\r\n/g,'\n').length}
 textoExibido(aviso:any){
  return !this.avisoLongo(aviso.descricao)
   ? aviso.descricao : this.previa(aviso.descricao).trimEnd()+'…';
 }
 abrirLeitura(aviso:any){
  this.avisoSelecionado=aviso;
  this.leitura.nativeElement.showModal();
  this.leitura.nativeElement.scrollTop=0;
 }
 fecharLeitura(){this.leitura.nativeElement.close()}
 constructor(private api:ApiService,public auth:AuthService){}
 ngOnInit(){this.listar();this.timer=setInterval(()=>this.listar(),30000)}
 ngOnDestroy(){if(this.timer)clearInterval(this.timer)}
 private hoje(){
  const partes=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  return ['year','month','day'].map(tipo=>partes.find(p=>p.type===tipo)!.value).join('-');
 }
 expirado(aviso:any){return aviso.data<this.hoje()}
 get cursosVisiveis(){return this.filtro?this.cursos.filter(c=>c===this.filtro||c==='Geral'):this.cursos}
 get dataMinima(){
  const partes=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
  const valor=(tipo:string)=>partes.find(parte=>parte.type===tipo)!.value;
  const amanha=new Date(valor('year')+'-'+valor('month')+'-'+valor('day')+'T12:00:00Z');
  amanha.setUTCDate(amanha.getUTCDate()+1);
  return amanha.toISOString().slice(0,10);
 }
 porCurso(c:string){return this.avisos.filter(a=>(a.curso||'Geral')===c&&(this.situacao==='todos'||(this.situacao==='expirados'?this.expirado(a):!this.expirado(a))))}
 listar(){this.api.get<any[]>('/avisos?situacao='+this.situacao).subscribe({next:x=>{this.avisos=x;if(this.avisoSelecionado&&this.situacao==='ativos'&&this.expirado(this.avisoSelecionado))this.fecharLeitura()},error:e=>this.falha(e)})}
 editar(a:any=null){if(!this.auth.editorAvisos)return;this.erro='';this.form=a?{...a}:{data:this.dataMinima,curso:this.filtro||'Geral'};this.aberto=true}
 salvar(){
  if(!this.auth.editorAvisos||this.salvando)return;
  if(!this.form.data||this.form.data<this.dataMinima){
   this.erro='Escolha uma data de expiração a partir de amanhã.';
   return;
  }
  this.salvando=true;this.erro='';
  const r=this.form.id?this.api.put('/avisos/'+this.form.id,this.form):this.api.post('/avisos',this.form);
  r.subscribe({next:()=>{this.salvando=false;this.aberto=false;this.listar()},error:e=>{this.salvando=false;this.falha(e)}});
 }
 excluir(id:number){if(this.auth.editorAvisos&&confirm('Excluir aviso?'))this.api.delete('/avisos/'+id).subscribe({next:()=>this.listar(),error:e=>this.falha(e)})}
 private falha(e:any){this.erro=e.status===401?'Entre novamente para acessar os avisos.':e.status===403?'Somente administradores e coordenadores podem alterar avisos.':e.error?.erro||'Não foi possível acessar os avisos.'}
}

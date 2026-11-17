import {Injectable} from '@angular/core';
import {HttpClient,HttpResponse} from '@angular/common/http';
import {Observable,of,from,firstValueFrom} from 'rxjs';
import {AnexosService} from './anexos.service';

type Store={pastas:any[];arquivos:any[];atas:any[];alunos:any[];avisos:any[];usuarios:any[]};

@Injectable({providedIn:'root'})
export class ApiService{
 readonly base='http://localhost:8080/api';private get key(){const sessao=JSON.parse(localStorage.getItem('sessao')||'null');return 'gestao-academica-conta-'+(sessao?.id??'anonimo')}
 constructor(private http:HttpClient,private anexos:AnexosService){if(!localStorage.getItem(this.key))this.save({...this.seed(),atas:[]})}
 get<T>(p:string):Observable<T>{
  if(p==='/atas-atividades')return from(this.listarAtas<T>());
  if(this.real(p))return this.http.get<T>(this.base+p,{withCredentials:true});const s=this.store();
  if(p.startsWith('/documentos')){const q=new URLSearchParams(p.split('?')[1]||'');const parent=q.has('pastaId')?Number(q.get('pastaId')):null;return of({pastas:s.pastas.filter(x=>(x.pai?.id??null)===parent),arquivos:s.arquivos.filter(x=>(x.pastaId??null)===parent)} as T)}
  if(p==='/atas-atividades')return of(s.atas as T);
  if(p==='/alunos')return of(s.alunos.map(a=>({...a,horasAprovadas:this.total(a)})) as T);
  if(p.startsWith('/alunos/')){const a=s.alunos.find(x=>x.id===Number(p.split('/').pop()));return of({...a,horasAprovadas:this.total(a)} as T)}
  if(p==='/usuarios')return of(s.usuarios.map(({senha,...u})=>u) as T);
  if(p==='/avisos')return of(s.avisos as T);return of(undefined as T);
 }
 post<T>(p:string,b:any):Observable<T>{
  if(this.real(p))return this.http.post<T>(this.base+p,b,{withCredentials:true});const s=this.store();
  if(p==='/documentos/pastas'){const x={id:this.id(),nome:b.nome,pai:b.paiId?{id:Number(b.paiId)}:null};s.pastas.push(x);this.save(s);return of(x as T)}
  if(p==='/documentos/arquivos'){const f=b.get('arquivo') as File;const x={id:this.id(),nome:f.name,tamanho:f.size,pastaId:b.get('pastaId')?Number(b.get('pastaId')):null};s.arquivos.push(x);this.save(s);return of(x as T)}
  if(p==='/atas-atividades')return new Observable<T>(o=>{(b.get('dados') as Blob).text().then(async t=>{const x={...JSON.parse(t),id:this.id()};const arquivo=b.get('arquivo') as File|null;if(arquivo){await this.anexos.salvar(x.id,arquivo);x.arquivoNome=arquivo.name;}s.atas.push(x);this.save(s);o.next(x as T);o.complete()}).catch(e=>o.error(e));});
  if(p==='/atas-atividades/sincronizacao'||p==='/atas-atividades/analisar')return of({errosPortugues:['Nenhum erro ortográfico evidente.'],errosGramaticais:['Revise a concordância e a pontuação antes da versão final.'],sugestoes:['Inclua responsáveis, prazos e decisões em tópicos objetivos.']} as T);
  if(p==='/alunos'){const x={...b,id:this.id(),certificados:[]};s.alunos.push(x);this.save(s);return of(x as T)}
  if(p==='/usuarios'){const x={...b,id:this.id(),ativo:b.ativo??true};s.usuarios.push(x);this.save(s);const {senha,...u}=x;return of(u as T)}
  if(p==='/certificados'){const a=s.alunos.find(x=>x.id===Number(b.get('alunoId')));const f=b.get('arquivo') as File;const x={id:this.id(),nome:b.get('nome'),quantidadeHoras:Number(b.get('quantidadeHoras')),status:'PENDENTE',observacao:null,arquivoNome:f?.name};a.certificados.push(x);this.save(s);return of(x as T)}
  if(p==='/avisos'){const x={...b,id:this.id()};s.avisos.unshift(x);this.save(s);return of(x as T)}return of(b as T);
 }
 put<T>(p:string,b:any):Observable<T>{
  if(this.real(p))return this.http.put<T>(this.base+p,b,{withCredentials:true});const s=this.store();const parts=p.split('/').filter(Boolean);const id=Number(parts.at(-1)==='avaliar'?parts.at(-2):parts.at(-1));
  if(p.startsWith('/certificados/'))for(const a of s.alunos){const c=a.certificados.find((x:any)=>x.id===id);if(c){c.status=b.status;c.horasAprovadas=b.horasAprovadas??null;c.observacao=b.motivo??null;this.save(s);return of(c as T)}}
  if(p.startsWith('/usuarios/')){const i=s.usuarios.findIndex(x=>x.id===id);if(i<0)throw new Error('Usuário não encontrado');s.usuarios[i]={...s.usuarios[i],...b,id,senha:b.senha||s.usuarios[i].senha};this.save(s);const {senha,...u}=s.usuarios[i];return of(u as T)}
  if(p.startsWith('/atas-atividades/')){const x=s.atas.find(x=>x.id===id);if(!x)throw new Error('Registro não encontrado');x.arquivoNome=b.arquivoNome;this.save(s);return of(x as T)}
  if(p.startsWith('/avisos/')){const i=s.avisos.findIndex(x=>x.id===id);s.avisos[i]={...b,id};this.save(s);return of(s.avisos[i] as T)}return of(b as T);
 }
 delete(p:string):Observable<unknown>{
  if(this.real(p))return this.http.delete(this.base+p,{withCredentials:true});const s=this.store();const id=Number(p.split('/').pop());
  if(p.startsWith('/documentos/arquivos/'))s.arquivos=s.arquivos.filter(x=>x.id!==id);if(p.startsWith('/avisos/'))s.avisos=s.avisos.filter(x=>x.id!==id);if(p.startsWith('/atas-atividades/'))s.atas=s.atas.filter(x=>x.id!==id);this.save(s);return of({});
 }
 blob(p:string):Observable<Blob>{if(this.real(p))return this.http.get(this.base+p,{responseType:'blob',withCredentials:true});if(p==='/alunos/exportar')return of(new Blob(['Matrícula;Nome;Curso;Horas aprovadas\n001;João Silva;Sistemas de Informação;60'],{type:'text/csv'}));return of(new Blob(['Arquivo demonstrativo do mockup acadêmico.'],{type:'text/plain'}))}
 postBlob(p:string,b:FormData):Observable<HttpResponse<Blob>>{return this.http.post(this.base+p,b,{observe:'response',responseType:'blob',withCredentials:true})}
 private async listarAtas<T>():Promise<T>{
  const chave=this.key;
  const antigas=this.store().atas;
  if(antigas.length&&!localStorage.getItem(chave+':atas-migradas')){
   await firstValueFrom(this.http.post(this.base+'/atas-atividades/importar',antigas,{withCredentials:true}));
   localStorage.setItem(chave+':atas-migradas','1');
  }
  return firstValueFrom(this.http.get<T>(this.base+'/atas-atividades',{withCredentials:true}));
 }
 private real(p:string){return p.startsWith('/atas-atividades')||p.startsWith('/documentos')||p.startsWith('/avisos')||p.startsWith('/usuarios')||p.startsWith('/auth/')||p.startsWith('/certificados')||p==='/atas-atividades/sincronizacao'||p==='/atas-atividades/analisar'||p==='/atas-atividades/analisar-documento'}
 private store():Store{if(!localStorage.getItem(this.key))this.save({...this.seed(),atas:[]});const s=JSON.parse(localStorage.getItem(this.key)!) as Store;if(!s.usuarios){s.usuarios=this.seed().usuarios;this.save(s)}return s}private save(s:Store){localStorage.setItem(this.key,JSON.stringify(s))}private id(){return Date.now()+Math.floor(Math.random()*1000)}private total(a:any){return(a?.certificados||[]).filter((c:any)=>c.status==='APROVADO').reduce((n:number,c:any)=>n+(c.horasAprovadas||0),0)}
 private seed():Store{return{pastas:[{id:11,nome:'Regulamentos',pai:null},{id:12,nome:'Calendários',pai:null},{id:13,nome:'Projetos',pai:null},{id:14,nome:'2026',pai:{id:12}}],arquivos:[{id:21,nome:'regulamento-curso.pdf',tamanho:248320,pastaId:null},{id:22,nome:'calendario-academico.docx',tamanho:92740,pastaId:12}],atas:[{id:31,titulo:'Reunião do Colegiado',descricao:'Reunião realizada para definir o calendário de avaliações e os responsáveis pelas atividades do semestre.',tipo:'ATA',data:'2026-08-15'},{id:32,titulo:'Semana Acadêmica',descricao:'Organização das palestras, oficinas e inscrições para a Semana Acadêmica.',tipo:'ATIVIDADE',data:'2026-09-10'}],alunos:[{id:41,nome:'João Silva',matricula:'001',curso:'Sistemas de Informação',turma:'2024.1',certificados:[{id:51,nome:'Curso Java',quantidadeHoras:20,horasAprovadas:20,status:'APROVADO',observacao:null},{id:52,nome:'Semana Acadêmica',quantidadeHoras:30,horasAprovadas:30,status:'APROVADO',observacao:null},{id:53,nome:'Curso Excel',quantidadeHoras:10,status:'PENDENTE',observacao:null}]},{id:42,nome:'Maria Souza',matricula:'002',curso:'Administração',turma:'2023.2',certificados:[{id:54,nome:'Gestão de Projetos',quantidadeHoras:40,horasAprovadas:40,status:'APROVADO',observacao:null}]}],avisos:[{id:61,titulo:'Prazo para entrega de certificados',descricao:'Os certificados devem ser enviados até 30 de setembro.',data:'2026-08-20'},{id:62,titulo:'Reunião de professores',descricao:'Reunião do colegiado na próxima sexta-feira, às 14h.',data:'2026-08-18'}],usuarios:[{id:71,nome:'Coordenador',email:'admin@faculdade.edu.br',senha:'Admin@123',perfil:'ADMIN',ativo:true}]}}
}

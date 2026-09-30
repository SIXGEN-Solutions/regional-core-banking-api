package com.regional.corebanking.payment.infrastructure.amplitude;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import java.sql.*; import java.time.*; import java.time.format.*; import java.util.*;
final class PaymentExecutionBankingContextResolver {
 private static final DateTimeFormatter D=DateTimeFormatter.ofPattern("ddMMyyyy"); private static final long MAX=999999L;
 private final PaymentExecutionSqlDialect q;
 PaymentExecutionBankingContextResolver(PaymentExecutionSqlDialect q){this.q=q;}
 PaymentExecutionBankingContext resolve(Connection c,String ope,String dev)throws SQLException{
  if(ope==null||ope.isBlank())throw new IllegalArgumentException("providerEvent.operationCode is required");
  if(dev==null||dev.isBlank())throw un("Core Banking account currency could not be determined");
  LocalDate d=date(c); boolean n=night(c); String t=n?"BKEVE_EOD":"BKEVE"; String eve=next(c,ope.strip(),t);
  Set<LocalDate> h=holidays(c,d); return new PaymentExecutionBankingContext(eve,d,n,t,previous(d,h),next(d,h),"XAF");
 }
 private String next(Connection c,String ope,String t)throws SQLException{
  long last=max(c,ope,t), candidate=last==MAX?1:last+1;
  for(long attempts=0;attempts<MAX;attempts++){
   String f="%06d".formatted(candidate); if(!exists(c,f,ope,t))return f;
   candidate++; candidate=candidate==MAX?1:candidate; // exact legacy searchNextEve collision wrap
  } throw un("No available Core Banking event number for operationCode");
 }
 private long max(Connection c,String ope,String t)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(q.findMaxEventNumber(t))){s.setString(1,ope);try(ResultSet r=s.executeQuery()){if(!r.next())return 0;String v=r.getString("eve");if(v==null||v.isBlank())return 0;try{return Long.parseLong(v.strip());}catch(NumberFormatException e){throw un("Core Banking maximum event number is invalid");}}}
 }
 private boolean exists(Connection c,String eve,String ope,String t)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(q.countEventNumber(t))){s.setString(1,eve);s.setString(2,ope);try(ResultSet r=s.executeQuery()){if(!r.next())throw un("Core Banking event collision check returned no result");return r.getLong("nbr")!=0;}}
 }
 private LocalDate date(Connection c)throws SQLException{
  String v=val(c,q.findAccountingDatePrimary(),"mnt2");if(v==null||v.equalsIgnoreCase("0")||v.strip().length()==1)v=val(c,q.findAccountingDateFallback(),"mnt1");if(v==null)throw un("Core Banking accounting date is null");v=v.strip();if(v.length()==7)v="0"+v;try{return LocalDate.parse(v,D);}catch(DateTimeParseException e){throw un("Core Banking accounting date is invalid");}
 }
 private String val(Connection c,String sql,String col)throws SQLException{try(PreparedStatement s=c.prepareStatement(sql)){s.setString(1,"001");s.setString(2,"00099");try(ResultSet r=s.executeQuery()){return r.next()?r.getString(col):null;}}}
 private boolean night(Connection c)throws SQLException{try(PreparedStatement s=c.prepareStatement(q.findNightMode());ResultSet r=s.executeQuery()){return r.next()&&r.getInt("mnt4")==1;}}
 private Set<LocalDate> holidays(Connection c,LocalDate d)throws SQLException{Set<LocalDate> h=new HashSet<>();load(c,d.getYear()-1,h);load(c,d.getYear(),h);load(c,d.getYear()+1,h);return h;}
 private void load(Connection c,int y,Set<LocalDate> h)throws SQLException{try(PreparedStatement s=c.prepareStatement(q.findHolidays())){s.setString(1,Integer.toString(y));try(ResultSet r=s.executeQuery()){while(r.next()){java.sql.Date x=r.getDate("jourfer");;if(x!=null)h.add(x.toLocalDate());}}}}
 private static LocalDate previous(LocalDate d,Set<LocalDate> h){LocalDate x=d.minusDays(1);while(!working(x,h))x=x.minusDays(1);return x;}
 private static LocalDate next(LocalDate d,Set<LocalDate> h){LocalDate x=d.plusDays(1);while(!working(x,h))x=x.plusDays(1);return x;}
 private static boolean working(LocalDate d,Set<LocalDate> h){return d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY&&!h.contains(d);}
 private static PaymentExecutionUnavailableException un(String m){return new PaymentExecutionUnavailableException(m);}
}

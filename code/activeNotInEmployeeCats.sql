Alter session set current_schema = title9stg;

select distinct p.code, psd.on_sale, p.DATE_CREATED, sum(i.stock)
from product p inner join
product_category_link pcl on pcl.PRODUCT_ID = p.PRODUCT_ID inner join
category c on c.CATEGORY_ID = pcl.CATEGORY_ID inner join
product_sku_link psl on psl.PRODUCT_ID = p.PRODUCT_ID inner join
site_product_link spl on spl.product_id = p.product_id and spl.site_id = 1 inner join
inventory i on i.SKU_ID = psl.SKU_ID inner join
product_site_data psd on psd.PRODUCT_ID = p.PRODUCT_ID and psd.SITE_ID = 1
where p.status = 1 and p.DELETE_FLAG = 0 and
regexp_like (p.code, '^\d{6}$') and
p.code not in (select distinct p.code
  from product p inner join
  product_category_link pcl on pcl.PRODUCT_ID = p.PRODUCT_ID inner join
  category c on c.CATEGORY_ID = pcl.CATEGORY_ID
  where c.code in ('EMP_BRAS', 'EMP_TOPS', 'EMP_BOTTOMS',
  'EMP_DRESSES', 'EMP_SWIM', 'EMP_SHOES', 'EMP_SALE', 'BLACKOUT'))
group by p.code, psd.on_sale, p.DATE_CREATED
order by psd.on_sale desc, sum(i.stock) desc;
